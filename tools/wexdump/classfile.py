#!/usr/bin/env python3
"""Minimal but complete JVM class file reader/writer.

Only what the signature-recovery pipeline needs: read the constant pool plus the
class-level attributes, append new Utf8 constants, and append a class-level
`Signature` attribute. Everything else is kept byte-identical so a round trip is
a no-op (see `selftest`).

Why hand-roll it: the payload has 1163 classes and we need to touch one attribute
in a subset of them. Spinning up `javap` 1163 times takes minutes, and ASM would
mean another dependency in a directory that ships only .py/.java helpers.
"""

import struct


# ---------------------------------------------------------------- constants

UTF8, INTEGER, FLOAT, LONG, DOUBLE = 1, 3, 4, 5, 6
CLAS, STRING = 7, 8
FIELDREF, METHODREF, IMETHODREF, NAMEANDTYPE = 9, 10, 11, 12
METHODHANDLE, METHODTYPE = 15, 16
DYNAMIC, INVOKEDYNAMIC, MODULE, PACKAGE = 17, 18, 19, 20

# tag -> how many bytes of payload follow (excluding the tag byte)
_SIZES = {
    3: 4, 4: 4, 5: 8, 6: 8,
    7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
    15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2,
}


class ClassFile:
    def __init__(self, data):
        self.data = data
        self.parse()

    # -------------------------------------------------------------- parse
    def parse(self):
        d = self.data
        if d[:4] != b'\xca\xfe\xba\xbe':
            raise ValueError('not a class file')
        self.magic, self.minor, self.major = struct.unpack('>IHH', d[:8])
        self.cp_count = struct.unpack('>H', d[8:10])[0]
        self.cp_start = 10
        self.cp = [None] * self.cp_count      # index 0 unused
        pos = 10
        i = 1
        while i < self.cp_count:
            tag = d[pos]
            if tag != UTF8 and tag not in _SIZES:
                raise ValueError('bad cp tag %d at %d' % (tag, pos))
            size = _SIZES.get(tag, 0)
            if tag == UTF8:
                ln = struct.unpack('>H', d[pos + 1:pos + 3])[0]
                self.cp[i] = (tag, d[pos + 3:pos + 3 + ln].decode('utf-8', 'replace'))
                pos += 3 + ln
            else:
                raw = d[pos + 1:pos + 1 + size]
                self.cp[i] = (tag, raw)
                pos += 1 + size
            i += 2 if tag in (LONG, DOUBLE) else 1
        self.cp_end = pos

        self.access, self.this_class, self.super_class = struct.unpack('>HHH', d[pos:pos + 6])
        pos += 6
        n = struct.unpack('>H', d[pos:pos + 2])[0]
        self.interfaces = list(struct.unpack('>%dH' % n, d[pos + 2:pos + 2 + 2 * n]))
        pos += 2 + 2 * n
        self.members_start = pos            # fields + methods, untouched by us
        self.tail = d[pos:]                 # everything from fields to EOF

    # ------------------------------------------------------------ helpers
    def utf8(self, idx):
        entry = self.cp[idx]
        return entry[1] if entry and entry[0] == UTF8 else None

    def classname(self, idx):
        if idx == 0:
            return None
        entry = self.cp[idx]
        if not entry or entry[0] != CLAS:
            return None
        return self.utf8(struct.unpack('>H', entry[1])[0])

    @property
    def name(self):
        return self.classname(self.this_class)

    @property
    def supername(self):
        return self.classname(self.super_class)

    def find_utf8(self, s):
        for i, entry in enumerate(self.cp):
            if entry and entry[0] == UTF8 and entry[1] == s:
                return i
        return 0

    def class_level_attributes(self):
        """Parse the trailing attributes table.

        Returns ([name, bytes] pairs, offset of the u2 attribute_count field)
        relative to the members block. The attribute table is the last section
        of a class file, so the entries themselves end at len(tail)."""
        tail = self.tail
        pos = 0
        for _ in range(2):                              # fields, then methods
            if pos + 2 > len(tail):
                return [], 0
            n = struct.unpack('>H', tail[pos:pos + 2])[0]
            pos += 2
            for _ in range(n):
                pos += 6                                # access, name, desc
                ac = struct.unpack('>H', tail[pos:pos + 2])[0]
                pos += 2
                for _ in range(ac):
                    al = struct.unpack('>I', tail[pos + 2:pos + 6])[0]
                    pos += 6 + al
        if pos + 2 > len(tail):
            return [], 0
        count_pos = pos
        ac = struct.unpack('>H', tail[pos:pos + 2])[0]
        pos += 2
        out = []
        for _ in range(ac):
            ni = struct.unpack('>H', tail[pos:pos + 2])[0]
            al = struct.unpack('>I', tail[pos + 2:pos + 6])[0]
            out.append((self.utf8(ni), tail[pos + 6:pos + 6 + al]))
            pos += 6 + al
        return out, count_pos

    def has_signature(self):
        attrs, _ = self.class_level_attributes()
        return any(name == 'Signature' for name, _ in attrs)

    # ------------------------------------------------------------- write
    def to_bytes(self, add_signature=None):
        """Serialize. `add_signature` appends one class-level Signature attr."""
        cp = self.cp[:]
        count = self.cp_count

        def add_utf8(s):
            nonlocal count
            idx = self.find_utf8(s)
            if idx:
                return idx
            raw = s.encode('utf-8')
            cp.append((UTF8, s))
            count += 1
            return count - 1

        attr_bytes = b''
        extra = 0
        if add_signature is not None:
            # Signature is { u2 signature_index; } -- the string lives in the
            # constant pool, the attribute body is just the index.
            name_idx = add_utf8('Signature')
            sig_idx = add_utf8(add_signature)
            attr_bytes = struct.pack('>HIH', name_idx, 2, sig_idx)
            extra = 1

        # rebuild constant pool
        out = bytearray()
        out += struct.pack('>IHH', self.magic, self.minor, self.major)
        out += struct.pack('>H', count)
        for entry in cp[1:]:
            if entry is None:                       # the second slot of a long/double
                continue
            tag, val = entry
            out += bytes([tag])
            if tag == UTF8:
                raw = val.encode('utf-8')
                out += struct.pack('>H', len(raw)) + raw
            else:
                out += val
        # header tail: access..interfaces, then the original members block
        hdr = struct.pack('>HHH', self.access, self.this_class, self.super_class)
        hdr += struct.pack('>H', len(self.interfaces))
        if self.interfaces:
            hdr += struct.pack('>%dH' % len(self.interfaces), *self.interfaces)
        out += hdr
        out += self.tail

        if extra:
            attrs, count_pos = self.class_level_attributes()
            base = len(out) - len(self.tail)           # start of the members block
            abs_pos = base + count_pos                 # position of attribute_count
            new_count = struct.pack('>H', len(attrs) + extra)
            # The attribute table is the final section, so the new attribute goes
            # at the very end -- right after the existing entries.
            return bytes(out[:abs_pos]) + new_count + bytes(out[abs_pos + 2:]) + attr_bytes
        return bytes(out)


def selftest(path):
    """Round trip must reproduce the input byte for byte."""
    with open(path, 'rb') as f:
        raw = f.read()
    cf = ClassFile(raw)
    rebuilt = cf.to_bytes()
    assert rebuilt == raw, 'round trip mismatch on ' + path
    return cf


if __name__ == '__main__':
    import sys
    for p in sys.argv[1:]:
        c = selftest(p)
        print('ok %-60s %s' % (c.name, 'sig' if c.has_signature() else '-'))
