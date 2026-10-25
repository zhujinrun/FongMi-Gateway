"""Restore the `new`/`<init>` pair that d2j destroyed, using the dex as ground truth.

d2j rewrites `new-instance X ; invoke-direct A.<init>` (A = ancestor of X, or
Object after R8 constructor lifting) into something the JVM verifier accepts but
which loses X -- most often `new java/lang/Object`.  Restoring it needs both the
real type and the real init owner.

Two passes:
  1. scan every method, LCS-align the JVM `new` sequence against the dex one,
     collect the operand writes we need plus the constants they reference;
  2. grow the constant pool once (Utf8/NameAndType/Class/Methodref) and apply.

LCS is essential: d2j does not preserve instruction order, so a positional zip
shifts unrelated sites and turns a fix into a corruption.

Usage: fixalign.py <class-root> <pairs.json> <report.txt>
"""
import json, os, struct, sys

LEN = {}
for o in range(0x00, 0x10): LEN[o] = 1
for o in range(0x1a, 0x36): LEN[o] = 1
for o in range(0x3b, 0x84): LEN[o] = 1
LEN.update({0x10: 2, 0x11: 3, 0x12: 2, 0x13: 3, 0x14: 3,
            0x15: 2, 0x16: 2, 0x17: 2, 0x18: 2, 0x19: 2,
            0x36: 2, 0x37: 2, 0x38: 2, 0x39: 2, 0x3a: 2,
            0x84: 3})
for o in range(0x85, 0x99): LEN[o] = 1
for o in range(0x99, 0xa9): LEN[o] = 3
LEN[0xa9] = 2
for o in range(0xac, 0xb2): LEN[o] = 1
for o in range(0xb2, 0xb6): LEN[o] = 3
LEN.update({0xb6: 3, 0xb7: 3, 0xb8: 3, 0xb9: 5, 0xba: 5, 0xbb: 3, 0xbc: 2, 0xbd: 3,
            0xbe: 1, 0xbf: 1, 0xc0: 3, 0xc1: 3, 0xc2: 1, 0xc3: 1, 0xc5: 4,
            0xc6: 3, 0xc7: 3, 0xc8: 5, 0xc9: 5})


def cp_parse(b):
    cnt = struct.unpack_from('>H', b, 8)[0]
    pos = 10
    i = 1
    info = {}
    while i < cnt:
        tag = b[pos]
        pos += 1
        if tag == 1:
            l = struct.unpack_from('>H', b, pos)[0]
            info[i] = ('utf8', b[pos + 2:pos + 2 + l].decode('utf-8', 'replace'))
            pos += 2 + l
        elif tag in (3, 4):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            info[i] = (tag, struct.unpack_from('>H', b, pos)[0])
            pos += 2
        elif tag in (9, 10, 11):
            cls_i, nat_i = struct.unpack_from('>HH', b, pos)
            info[i] = (tag, cls_i, nat_i)
            pos += 4
        elif tag == 12:
            ni, di = struct.unpack_from('>HH', b, pos)
            info[i] = (12, ni, di)
            pos += 4
        elif tag in (17, 18):
            pos += 4
        elif tag == 15:
            pos += 3
        else:
            raise Exception('bad cp tag %d idx=%d' % (tag, i))
        i += 1
    return pos, cnt, info


class Cp:
    """Read-only view of a constant pool."""

    def __init__(self, info):
        self.info = info
        self.utf8 = {i: v[1] for i, v in info.items() if v[0] == 'utf8'}
        self.by_utf8 = {v: i for i, v in self.utf8.items()}
        self.cls = {self.utf8.get(v[1], ''): i for i, v in info.items() if v[0] == 7}
        self.nat = {}
        self.mref = {}
        for i, v in info.items():
            if v[0] == 12:
                self.nat[(v[1], v[2])] = i
            elif v[0] == 10:
                self.mref[(v[1], v[2])] = i

    def class_name(self, idx):
        v = self.info.get(idx)
        return self.utf8.get(v[1], '') if v and v[0] in (7, 10) else ''

    def is_init(self, idx):
        v = self.info.get(idx)
        if not v or v[0] != 10:
            return False
        nat = self.info.get(v[2])
        return bool(nat) and nat[0] == 12 and self.utf8.get(nat[1]) == '<init>'

    def mref_desc(self, idx):
        v = self.info.get(idx)
        if not v or v[0] != 10:
            return None
        nat = self.info.get(v[2])
        return self.utf8.get(nat[2]) if nat and nat[0] == 12 else None


def methods_off(b):
    pos, cnt, info = cp_parse(b)
    utf8 = {i: v[1] for i, v in info.items() if v[0] == 'utf8'}
    p = pos + 6
    ic = struct.unpack_from('>H', b, p)[0]
    p += 2 + 2 * ic

    def skip_attrs(p):
        ac = struct.unpack_from('>H', b, p)[0]
        p += 2
        for _ in range(ac):
            al = struct.unpack_from('>I', b, p + 2)[0]
            p += 6 + al
        return p

    fc = struct.unpack_from('>H', b, p)[0]
    p += 2
    for _ in range(fc):
        p += 6
        p = skip_attrs(p)
    mc = struct.unpack_from('>H', b, p)[0]
    p += 2
    out = {}
    for _ in range(mc):
        acc, ni, di = struct.unpack_from('>HHH', b, p)
        p += 6
        ac = struct.unpack_from('>H', b, p)[0]
        p += 2
        key = '%s|%s' % (utf8.get(ni, '?'), utf8.get(di, '?'))
        for _ in range(ac):
            an = struct.unpack_from('>H', b, p)[0]
            al = struct.unpack_from('>I', b, p + 2)[0]
            body = p + 6
            if utf8.get(an) == 'Code':
                clen = struct.unpack_from('>I', b, body + 4)[0]
                out.setdefault(key, (body + 8, clen))
            p = body + al
    return out


def _advance(b, p, start):
    op = b[p]
    if op == 0xaa:
        q = (p - start + 4) & ~3
        q += start
        lo, hi = struct.unpack_from('>ii', b, q + 4)
        return q + 12 + 4 * (hi - lo + 1)
    if op == 0xab:
        q = (p - start + 4) & ~3
        q += start
        np = struct.unpack_from('>i', b, q + 4)[0]
        return q + 8 + 8 * np
    if op == 0xc4:
        return p + (6 if b[p + 1] == 0x84 else 4)
    l = LEN.get(op)
    return p + (l if l else 1)


def find_init(b, start, end, cp):
    depth = 0
    p = start
    while p < end:
        op = b[p]
        if op == 0xbb:
            depth += 1
        elif op == 0xb7 and cp.is_init(struct.unpack_from('>H', b, p + 1)[0]):
            depth -= 1
            if depth == 0:
                return p + 1
        p = _advance(b, p, start)
    return None


def walk_new(b, start, ln, cp):
    """[(new_operand_off, type, init_operand_off, init_owner, init_desc)]"""
    p = start
    end = start + ln
    res = []
    while p < end:
        if b[p] == 0xbb:
            typ = cp.class_name(struct.unpack_from('>H', b, p + 1)[0])
            io = find_init(b, p, end, cp)
            owner = cp.class_name(struct.unpack_from('>H', b, io)[0]) if io else None
            desc = cp.mref_desc(struct.unpack_from('>H', b, io)[0]) if io else None
            res.append((p + 1, typ, io, owner, desc))
            p += 3
            continue
        p = _advance(b, p, start)
    return res


def lcs_align(d, j):
    n, m = len(d), len(j)
    if n == 0 or m == 0:
        return []
    dp = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n - 1, -1, -1):
        di = d[i]
        for k in range(m - 1, -1, -1):
            dp[i][k] = dp[i + 1][k + 1] + 1 if di == j[k] else max(dp[i + 1][k], dp[i][k + 1])
    anchors = []
    i = k = 0
    while i < n and k < m:
        if d[i] == j[k]:
            anchors.append((i, k))
            i += 1
            k += 1
        elif dp[i + 1][k] >= dp[i][k + 1]:
            i += 1
        else:
            k += 1
    pairs = []
    pi = pk = 0
    for ai, ak in anchors + [(n, m)]:
        for t in range(min(ai - pi, ak - pk)):
            pairs.append((pi + t, pk + t))
        if ai < n and ak < m:
            pairs.append((ai, ak))
        pi, pk = ai + 1, ak + 1
    return pairs


class CpBuilder:
    """Append constants to a pool and hand back their indices."""

    def __init__(self, cp, next_index):
        self.cp = cp
        self.next = next_index
        self.chunks = []

    def _append(self, blob):
        idx = self.next
        self.chunks.append(blob)
        self.next += 1
        return idx

    def utf8(self, s):
        if s in self.cp.by_utf8:
            return self.cp.by_utf8[s]
        ub = s.encode('utf-8')
        idx = self._append(struct.pack('>BH', 1, len(ub)) + ub)
        self.cp.by_utf8[s] = idx
        return idx

    def cls(self, name):
        if name in self.cp.cls:
            return self.cp.cls[name]
        ni = self.utf8(name)
        idx = self._append(struct.pack('>BH', 7, ni))
        self.cp.cls[name] = idx
        return idx

    def nat(self, name, desc):
        ni, di = self.utf8(name), self.utf8(desc)
        if (ni, di) in self.cp.nat:
            return self.cp.nat[(ni, di)]
        idx = self._append(struct.pack('>BHH', 12, ni, di))
        self.cp.nat[(ni, di)] = idx
        return idx

    def mref(self, owner, name, desc):
        ci = self.cls(owner)
        nati = self.nat(name, desc)
        if (ci, nati) in self.cp.mref:
            return self.cp.mref[(ci, nati)]
        idx = self._append(struct.pack('>BHH', 10, ci, nati))
        self.cp.mref[(ci, nati)] = idx
        return idx


def fix(path, want, report):
    """want = {method: [[new_type, init_owner], ...]} in dex order"""
    b = bytearray(open(path, 'rb').read())
    pos, cnt, info = cp_parse(b)
    cp = Cp(info)
    moff = methods_off(b)

    cls_writes = []    # (offset, type)
    mref_writes = []   # (offset, owner, desc)
    for key, pairs in want.items():
        loc = moff.get(key)
        if not loc:
            continue
        sites = walk_new(b, loc[0], loc[1], cp)
        jvm = [s[1] for s in sites]
        if len(jvm) != len(pairs):
            report.append('%s %s: dex=%d jar=%d' % (os.path.basename(path), key, len(pairs), len(jvm)))
        for di, ji in lcs_align([p[0] for p in pairs], jvm):
            if di >= len(pairs) or ji >= len(sites):
                continue
            typ, owner = pairs[di]
            off, cur_t, io, cur_owner, desc = sites[ji]
            if cur_t != typ:
                cls_writes.append((off, typ))
            if io and owner and cur_owner != owner and desc:
                mref_writes.append((io, owner, desc))

    if not cls_writes and not mref_writes:
        return 0

    # grow the pool once, then patch — offsets after the pool shift uniformly
    cb = CpBuilder(cp, cnt)
    for _, t in cls_writes:
        cb.cls(t)
    for _, owner, desc in mref_writes:
        cb.mref(owner, '<init>', desc)
    add = b''.join(cb.chunks)
    delta = 0
    if add:
        delta = len(add)
        b = bytearray(b[:8]) + struct.pack('>H', cb.next) + b[10:pos] + add + b[pos:]

    for off, t in cls_writes:
        struct.pack_into('>H', b, off + delta, cb.cls(t))
    for off, owner, desc in mref_writes:
        struct.pack_into('>H', b, off + delta, cb.mref(owner, '<init>', desc))
    open(path, 'wb').write(b)
    return len(cls_writes) + len(mref_writes)


def main(root, mapfile, reportfile):
    data = json.load(open(mapfile, encoding='utf-8'))
    report = []
    total = 0
    for desc, methods in data.items():
        internal = desc[1:-1]
        path = os.path.join(root, internal + '.class')
        if not os.path.exists(path):
            report.append('MISSING CLASS ' + internal)
            continue
        total += fix(path, methods, report)
    open(reportfile, 'w', encoding='utf-8').write('\n'.join(report))
    print('patched operands=%d, warnings=%d' % (total, len(report)))
    for r in report[:15]:
        print('  W', r)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3])
