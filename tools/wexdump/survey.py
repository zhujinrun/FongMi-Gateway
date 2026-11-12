#!/usr/bin/env python3
"""Structural survey of a translated wex payload: locate the classes that the
Gson recovery tables need (TypeToken base + its anonymous subclasses, the
@SerializedName annotation, the CatVod bean models).

R8 renames every internal class per build, so the tables in gsonparams.py /
gsonfields.py cannot be reused across payload generations. This script rebuilds
the *candidate lists* from structure alone; the mapping itself is still pinned
by bytecode evidence (see analyse_evidence output).

Usage:
    python analyze.py <jar> <mode> [args]
      survey                      counts + candidate classes
      token <n>                   subclasses of the class with >= n subclasses
      annot                       annotation interfaces with value()/alternate()
      beans                       bean-shaped classes
      sites <class>               who instantiates <class>, and checkcasts there
"""
import io
import os
import struct
import sys
import zipfile
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))

sys.path.insert(0, HERE)
from classfile import ClassFile            # noqa: E402
import bytecode                            # noqa: E402

ACC_INTERFACE = 0x0200
ACC_ANNOTATION = 0x2000
ACC_ABSTRACT = 0x0400
ACC_STATIC = 0x0008


def load(path):
    classes = {}
    with zipfile.ZipFile(path) as z:
        for item in z.infolist():
            if not item.filename.endswith('.class'):
                continue
            data = z.read(item.filename)
            cf = ClassFile(data)
            classes[cf.name] = (data, cf)
    return classes


def members(cf):
    fields, methods, _ = cf.parse_members()
    fl = [(cf.utf8(f[1]), cf.utf8(f[2]), f[0]) for f in fields]
    ml = [(cf.utf8(m[1]), cf.utf8(m[2]), m[0]) for m in methods]
    return fl, ml


def survey(classes):
    supers = defaultdict(list)
    for name, (_, cf) in classes.items():
        if cf.supername:
            supers[cf.supername].append(name)
    # classes with lots of subclasses
    print('== superclasses with many subclasses ==')
    for sup, subs in sorted(supers.items(), key=lambda kv: -len(kv[1]))[:12]:
        anon = 0
        for s in subs:
            fl, ml = members(classes[s][1])
            if not fl and all(m[0] == '<init>' for m in ml):
                anon += 1
        print(f'  {sup:<46} subs={len(subs):<4} empty-ctor-only={anon}')
    print()
    print('== annotations ==')
    for name, (_, cf) in classes.items():
        if cf.access & ACC_ANNOTATION:
            fl, ml = members(cf)
            sigs = sorted(f'{m[0]}{m[1]}' for m in ml)
            print(f'  {name}  ifaces={[cf.classname(i) for i in cf.interfaces]}')
            print(f'      {sigs}')
    print()
    print('== class count ==', len(classes))


def token(jar, top=6):
    classes = load(jar)
    supers = defaultdict(list)
    for name, (_, cf) in classes.items():
        if cf.supername:
            supers[cf.supername].append(name)
    scored = []
    for sup, subs in supers.items():
        if sup == 'java/lang/Object':
            continue
        empty = []
        for s in subs:
            fl, ml = members(classes[s][1])
            if not fl and all(m[0] == '<init>' for m in ml):
                empty.append(s)
        if len(empty) >= 3:
            scored.append((len(empty), sup, subs, empty))
    scored.sort(key=lambda r: -r[0])
    for anon, sup, subs, empty in scored[:top]:
        print(f'{sup} : subs={len(subs)} empty={anon}')
        if sup in classes:
            fl, ml = members(classes[sup][1])
            print(f'   base fields={fl}')
            print(f'   base methods={[m[0] + m[1] for m in ml]}')
        for s in sorted(empty):
            print(f'    empty: {s}')
        print()


def annot(jar):
    classes = load(jar)
    for name, (_, cf) in classes.items():
        if not (cf.access & ACC_ANNOTATION):
            continue
        fl, ml = members(cf)
        descs = [m[1] for m in ml]
        if '()Ljava/lang/String;' in descs and '()[Ljava/lang/String;' in descs:
            print(f'CANDIDATE {name}  methods={descs} fields={fl}')
        else:
            print(f'  other   {name}  methods={descs}')


def beans(jar):
    classes = load(jar)
    rows = []
    for name, (_, cf) in classes.items():
        if cf.access & (ACC_INTERFACE | ACC_ANNOTATION):
            continue
        fl, ml = members(cf)
        if not fl:
            continue
        types = defaultdict(int)
        for _, d, _acc in fl:
            types[d] += 1
        rows.append((len(fl), name, dict(types), fl, ml))
    rows.sort(key=lambda r: -r[0])
    for cnt, name, types, fl, ml in rows[:25]:
        desc = ','.join(f'{t}x{n}' for t, n in sorted(types.items()))
        print(f'{name:<40} n={cnt:<3} {desc}')
        print(f'    ctor/others: ' + '; '.join(f'{m[0]}{m[1]}' for m in ml[:8]))
        print(f'    fields: ' + ','.join(f[0] for f in fl))


def sites(jar, target):
    classes = load(jar)
    for name, (data, cf) in classes.items():
        for m in bytecode.methods(cf):
            if m.code is None:
                continue
            hit = False
            casts = []
            for off, op, operand in bytecode.scan(m.code):
                if op == bytecode.NEW and operand and cf.classname(operand) == target:
                    hit = True
                if op == bytecode.CHECKCAST and operand and cf.classname(operand):
                    casts.append(cf.classname(operand))
            if hit:
                print(f'{name}.{m.name}{m.desc}')
                print(f'    checkcasts: {sorted(set(casts))}')


def pkg(jar, prefix):
    """Bean-shaped classes under a package prefix."""
    classes = load(jar)
    rows = []
    for name, (_, cf) in classes.items():
        if not name.startswith(prefix):
            continue
        if cf.access & (ACC_INTERFACE | ACC_ANNOTATION):
            continue
        fl, ml = members(cf)
        if not fl:
            continue
        types = defaultdict(int)
        for _, d, _acc in fl:
            types[d] += 1
        rows.append((len(fl), name, dict(types), fl, ml))
    rows.sort(key=lambda r: -r[0])
    for cnt, name, types, fl, ml in rows:
        desc = ','.join(f'{t}x{n}' for t, n in sorted(types.items()))
        print(f'{name:<44} n={cnt:<3} {desc}')
        print(f'    ctor: ' + '; '.join(f'{m[0]}{m[1]}' for m in ml if m[0] == '<init>'))
        print(f'    fields: ' + ','.join(f'{f[0]}:{f[1]}' for f in fl))


def find(jar, pattern):
    """Find classes whose field-type histogram satisfies `desc:count,...`."""
    classes = load(jar)
    want = {}
    for part in pattern.split(','):
        desc, _, n = part.partition(':')
        want[desc] = int(n or 1)
    for name, (_, cf) in classes.items():
        if cf.access & (ACC_INTERFACE | ACC_ANNOTATION):
            continue
        fl, ml = members(cf)
        types = defaultdict(int)
        for _, d, _acc in fl:
            types[d] += 1
        if all(types.get(d, 0) >= n for d, n in want.items()) \
                and len(fl) <= sum(want.values()) + 4:
            print(f'{name:<44} n={len(fl)} '
                  + ','.join(f'{t}x{v}' for t, v in sorted(types.items())))
            print(f'    ctor: ' + '; '.join(f'{m[0]}{m[1]}' for m in ml if m[0] == '<init>'))
            print(f'    fields: ' + ','.join(f'{f[0]}:{f[1]}' for f in fl))


if __name__ == '__main__':
    jar = sys.argv[1]
    mode = sys.argv[2] if len(sys.argv) > 2 else 'survey'
    if mode == 'survey':
        survey(load(jar))
    elif mode == 'token':
        token(jar, int(sys.argv[3]) if len(sys.argv) > 3 else 6)
    elif mode == 'annot':
        annot(jar)
    elif mode == 'beans':
        beans(jar)
    elif mode == 'pkg':
        pkg(jar, sys.argv[3])
    elif mode == 'find':
        find(jar, sys.argv[3])
    elif mode == 'sites':
        sites(jar, sys.argv[3])
    else:
        print(__doc__)
