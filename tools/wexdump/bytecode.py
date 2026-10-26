#!/usr/bin/env python3
"""Just enough JVM bytecode reading to answer "which method creates this
anonymous TypeToken subclass, and what does the caller do with the result".

A full disassembler is not needed: we walk instructions linearly, resolve the
constant-pool operands of the handful of opcodes that matter (`new`,
`anewarray`, `checkcast`, `invoke*`) and ignore everything else.
"""

import struct

# operand byte counts per opcode (JVM SE 21, tables 6.5); variable-length ones
# are handled explicitly in `scan`.
_LEN = [1] * 256
for _op, _n in {
    0x10: 2, 0x11: 3, 0x12: 2, 0x13: 3, 0x14: 3,
    0x15: 2, 0x16: 2, 0x17: 2, 0x18: 2, 0x19: 2,
    0x36: 2, 0x37: 2, 0x38: 2, 0x39: 2, 0x3a: 2,
    0x84: 3,
    0xa9: 2,
    0xb2: 3, 0xb3: 3, 0xb4: 3, 0xb5: 3, 0xb6: 3, 0xb7: 3, 0xb8: 3,
    0xb9: 5, 0xba: 5,
    0xbb: 3, 0xbc: 2, 0xbd: 3,
    0xc0: 3, 0xc1: 3,
    0xc5: 4,
    0xc6: 3, 0xc7: 3, 0xc8: 5, 0xc9: 5,
}.items():
    _LEN[_op] = _n
for _op in range(0x99, 0xa9):        # if<cond>, goto, jsr
    _LEN[_op] = 3
for _op in range(0xac, 0xb2):        # *return
    _LEN[_op] = 1

NEW, ANEWARRAY, CHECKCAST, INSTANCEOF = 0xbb, 0xbd, 0xc0, 0xc1
GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD = 0xb2, 0xb3, 0xb4, 0xb5
INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE = 0xb6, 0xb7, 0xb8, 0xb9
INVOKEDYNAMIC = 0xba


def scan(code):
    """Yield (offset, opcode, operand_int_or_None)."""
    i, n = 0, len(code)
    while i < n:
        op = code[i]
        if op == 0xaa:                                   # tableswitch
            pad = (4 - (i + 1) % 4) % 4
            base = i + 1 + pad
            low = struct.unpack('>i', code[base + 4:base + 8])[0]
            high = struct.unpack('>i', code[base + 8:base + 12])[0]
            i = base + 12 + (high - low + 1) * 4
            continue
        if op == 0xab:                                   # lookupswitch
            pad = (4 - (i + 1) % 4) % 4
            base = i + 1 + pad
            npairs = struct.unpack('>i', code[base + 4:base + 8])[0]
            i = base + 8 + npairs * 8
            continue
        if op == 0xc4:                                   # wide
            i += 6 if code[i + 1] == 0x84 else 4
            continue
        ln = _LEN[op]
        if i + ln > n:
            raise ValueError('truncated instruction 0x%02x at %d/%d' % (op, i, n))
        operand = None
        if ln >= 2:
            if ln == 2:
                operand = code[i + 1]
            elif ln == 3:
                operand = struct.unpack('>H', code[i + 1:i + 3])[0]
            else:
                operand = struct.unpack('>H', code[i + 1:i + 3])[0]
        yield i, op, operand
        i += ln


class Method:
    def __init__(self, owner, name, desc, access, code):
        self.owner, self.name, self.desc, self.access, self.code = owner, name, desc, access, code

    def __repr__(self):
        return '%s.%s%s' % (self.owner.split('/')[-1], self.name, self.desc)


def methods(cf):
    """Parse the members block and return [Method] with code attached."""
    tail = cf.tail
    pos = 0
    out = []

    def walk(pos, keep):
        n = struct.unpack('>H', tail[pos:pos + 2])[0]
        pos += 2
        for _ in range(n):
            acc, ni, di = struct.unpack('>HHH', tail[pos:pos + 6])
            pos += 6
            ac = struct.unpack('>H', tail[pos:pos + 2])[0]
            pos += 2
            code = None
            for _ in range(ac):
                an = struct.unpack('>H', tail[pos:pos + 2])[0]
                al = struct.unpack('>I', tail[pos + 2:pos + 6])[0]
                body = tail[pos + 6:pos + 6 + al]
                if keep and cf.utf8(an) == 'Code' and len(body) > 8:
                    # max_stack, max_locals, code_length, then exactly code_length bytes.
                    # The exception table follows the code array, so slicing to the end
                    # of the attribute would feed non-instruction bytes to `scan`.
                    clen = struct.unpack('>I', body[4:8])[0]
                    code = body[8:8 + clen]
                pos += 6 + al
            if keep:
                out.append(Method(cf.name, cf.utf8(ni), cf.utf8(di), acc, code))
        return pos

    pos = walk(pos, False)      # fields
    walk(pos, True)             # methods
    return out


def members_of_type(cf, opcodes, target_class):
    """Methods whose code contains one of `opcodes` referencing `target_class`."""
    hits = []
    for m in methods(cf):
        if m.code is None:
            continue
        for off, op, operand in scan(m.code):
            if op in opcodes and operand is not None and cf.classname(operand) == target_class:
                hits.append(m)
                break
    return hits
