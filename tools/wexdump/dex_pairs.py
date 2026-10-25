"""Extract, for every dex method, the ordered list of (new-instance type, <init> owner).

Both symbols are needed: d2j corrupts the `new` type and, for R8's lifted
constructors, the init owner too.

Usage: dex_pairs.py <classes.dex> <out.json> [path/to/dexdump]
"""
import json, os, re, subprocess, sys

code_re = re.compile(r'^\s*[0-9a-f]+:\s+[0-9a-f ]+\|([0-9a-f]+):\s+(\S+)(.*)$')
cls_re = re.compile(r"^\s+Class descriptor\s+:\s+'(.+?)'")
mname_re = re.compile(r"^\s+name\s+:\s+'(.+?)'")
mtype_re = re.compile(r"^\s+type\s+:\s+'(.+?)'")
new_re = re.compile(r'v(\d+), (L[^;]+;)')
init_re = re.compile(r'\{v(\d+),?[^}]*\}, (L[^;]+;)\.<init>')


def main(dex, out, dexdump):
    cur_cls = None
    cur = None            # [name, desc, [[type, owner, reg], ...]]
    in_methods = False
    result = {}

    p = subprocess.Popen([dexdump, '-d', dex], stdout=subprocess.PIPE)
    for raw in p.stdout:
        line = raw.decode('utf-8', 'replace')
        m = cls_re.match(line)
        if m:
            cur_cls, cur, in_methods = m.group(1), None, False
            continue
        if line.strip().startswith('Direct methods') or line.strip().startswith('Virtual methods'):
            in_methods, cur = True, None
            continue
        if not in_methods:
            continue
        m = mname_re.match(line)
        if m:
            cur = [m.group(1), None, []]
            continue
        m = mtype_re.match(line)
        if m and cur is not None and cur[1] is None:
            cur[1] = m.group(1)
            result.setdefault(cur_cls, {})['%s|%s' % (cur[0], cur[1])] = cur[2]
            continue
        m2 = code_re.match(line)
        if not m2 or cur is None:
            continue
        op, rest = m2.group(2), m2.group(3)
        if op == 'new-instance':
            mm = new_re.search(rest)
            if mm:
                cur[2].append([mm.group(2)[1:-1], None, mm.group(1)])
            continue
        if op == 'invoke-direct':
            mm = init_re.search(rest)
            if not mm:
                continue
            reg, owner = mm.group(1), mm.group(2)[1:-1]
            for pair in reversed(cur[2]):          # nearest pending new on the same register
                if pair[1] is None and pair[2] == reg:
                    pair[1] = owner
                    break
    p.wait()

    for methods in result.values():
        for key, pairs in list(methods.items()):
            methods[key] = [[t, o if o else t] for t, o, _ in pairs]

    tot = sum(len(v) for c in result.values() for v in c.values())
    self_init = sum(1 for c in result.values() for v in c.values() for x in v if x[0] == x[1])
    print('classes=%d methods=%d pairs=%d selfInit=%d' % (
        len(result), sum(len(c) for c in result.values()), tot, self_init))
    json.dump(result, open(out, 'w'), ensure_ascii=False, indent=0)


if __name__ == '__main__':
    tool = sys.argv[3] if len(sys.argv) > 3 else os.environ.get('DEXDUMP', 'dexdump')
    main(sys.argv[1], sys.argv[2], tool)
