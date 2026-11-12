#!/usr/bin/env python3
"""Align two payload generations and print the SIGNATURES table for the new one.

Why alignment instead of re-derivation
--------------------------------------
The element type of a `new TypeToken<List<X>>(){}` is usually recoverable from
one build (the host class of the token, or the `merge/*` checkcast inside it),
but not always: in `r/b.a(String)` the only cast belongs to a different bean and
the element is the host itself, while in `n/g.a(...)` the host and the element
coincide for a different reason. A wrong type argument does not crash -- Gson
deserializes into the wrong shape and the source renders empty, which is the
exact failure this pipeline exists to fix.

So the *published* table, which is field-proven (97/100 sources returning real
data), is transferred rather than re-invented. Entries are matched by structural
profile, not by name:

    (host field count, host method count, normalized instantiating descriptor)

The descriptor's parameter types are R8-renamed per build (`y0/s` -> `z0/s`), so
every internal class in it is collapsed to `L*;`. When two old entries collapse
onto the same profile they must agree on the verdict, and the script checks that.

When the old verdict was "element == host", the new element is the new host.
Otherwise the new element is the single `merge/*` cast inside the new host.

Usage
-----
    python align.py <old.jar> <new.jar>

Prints the alignment plus a ready-to-paste `signatures` block for
`gsonparams.GENERATIONS`. The `gsonfields` entry (annotation class + the four
bean class names) still has to be filled in by hand -- `survey.py` locates all of
them by shape.
"""
import os
import sys
import zipfile
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from classfile import ClassFile   # noqa: E402
import bytecode                   # noqa: E402
import gsonparams                 # noqa: E402

PREFIX = 'com/github/catvod/spider/'


def load(path):
    out = {}
    with zipfile.ZipFile(path) as z:
        for it in z.infolist():
            if it.filename.endswith('.class'):
                cf = ClassFile(z.read(it.filename))
                out[cf.name] = cf
    return out


def members(cf):
    f, m, _ = cf.parse_members()
    return ([(cf.utf8(x[1]), cf.utf8(x[2])) for x in f],
            [(cf.utf8(x[1]), cf.utf8(x[2])) for x in m])


def analyse(classes):
    """-> token, sorted token subclasses, {subclass: [(host, name, desc)]}"""
    supers = defaultdict(list)
    for n, cf in classes.items():
        if cf.supername:
            supers[cf.supername].append(n)
    token, subs = None, []
    for sup, ss in supers.items():
        if sup not in classes:
            continue
        empty = [s for s in ss
                 if not members(classes[s])[0]
                 and all(m[0] == '<init>' for m in members(classes[s])[1])]
        if len(empty) >= 8:
            token, subs = sup, sorted(empty)
            break
    inst = defaultdict(list)
    for n, cf in classes.items():
        for m in bytecode.methods(cf):
            if m.code is None:
                continue
            for off, op, operand in bytecode.scan(m.code):
                if op == bytecode.NEW and operand and cf.classname(operand) in subs:
                    inst[cf.classname(operand)].append((n, m.name, m.desc))
    return token, subs, inst


def norm(desc):
    """Collapse internal class names in the parameter list to `L*;`."""
    head, _, ret = desc.rpartition(')')
    out, i = [], 0
    while i < len(head):
        if head[i] == 'L':
            i = head.index(';', i) + 1
            out.append('L*;')
        else:
            out.append(head[i])
            i += 1
    return ''.join(out) + ')' + ret


def verdict(arg):
    """Turn a stored type argument into ('pass'|'list'|'other', element).

    `gsonparams` stores the *whole* type argument, so a List entry reads
    `Ljava/util/List<Lcom/.../X;>;` and the element has to be unwrapped before
    it can be compared with the host name.
    """
    if arg.startswith('Ljava/util/LinkedHashMap'):
        return 'pass', None
    if arg.startswith('Ljava/util/List<L') and arg.endswith(';>;'):
        return 'list', arg[len('Ljava/util/List<L'):-len(';>;')]
    return 'other', arg


def host_casts(classes, host, exclude):
    casts = set()
    cf = classes[host]
    for m in bytecode.methods(cf):
        if m.code is None:
            continue
        for off, op, operand in bytecode.scan(m.code):
            if op == bytecode.CHECKCAST and operand:
                t = cf.classname(operand)
                if t and t.startswith(PREFIX + 'merge/') and t != exclude:
                    casts.add(t)
    return casts


def main(oldjar, newjar):
    oldgen = gsonparams.detect(zipfile.ZipFile(oldjar))
    newgen = gsonparams.detect(zipfile.ZipFile(newjar))
    if oldgen is newgen:
        raise SystemExit('both jars belong to the same generation (%s)'
                         % oldgen['token'])
    print(f'old gen {oldgen["token"]}')
    print(f'new gen {newgen["token"]}')

    old, new = load(oldjar), load(newjar)
    _, _, oinst = analyse(old)
    _, nsubs, ninst = analyse(new)

    oprof = defaultdict(list)
    for simple, (arg, _why) in oldgen['signatures'].items():
        sites = oinst.get(PREFIX + simple, [])
        if not sites:
            print(f'!! old {simple} has no instantiation site, skipped')
            continue
        host, mn, md = sites[0]
        fl, ml = members(old[host])
        kind, elem = verdict(arg)
        is_host = kind == 'list' and elem == host
        oprof[(len(fl), len(ml), norm(md))].append((simple, host, arg, is_host))

    out = {}
    for s in nsubs:
        sites = ninst.get(s, [])
        if not sites:
            continue
        host, mn, md = sites[0]
        fl, ml = members(new[host])
        key = (len(fl), len(ml), norm(md))
        if key not in oprof:
            print(f'?? {s}: no old match for {host} {key}')
            continue
        cands = oprof[key]
        if len({(verdict(c[2])[0], c[3]) for c in cands}) > 1:
            print(f'!! {s}: ambiguous old match {[c[0] for c in cands]}')
            continue
        simple, ohost, arg, is_host = cands[0]
        kind, elem = verdict(arg)
        if kind == 'pass':
            newarg = arg
        elif kind == 'other':
            print(f'!! {s}: unexpected old argument {arg}')
            continue
        elif is_host:
            newarg = 'Ljava/util/List<L%s;>;' % host
        else:
            casts = host_casts(new, host, s)
            if len(casts) != 1:
                print(f'!! {s}: expected 1 cast in {host}, got {sorted(casts)}')
                continue
            newarg = 'Ljava/util/List<L%s;>;' % casts.pop()
        out[s] = (newarg, simple)
        print(f'{s:<40} <- {simple:<12} {host:<42} {newarg}')

    print('\n--- signatures (paste into gsonparams.GENERATIONS) ---')
    for s in sorted(out):
        arg, old_simple = out[s]
        print(f"        '{s.replace(PREFIX, '')}': (")
        print(f"            '{arg}',")
        print(f"            'aligned from {old_simple} by structural profile'),")

    no_site = [s.replace(PREFIX, '') for s in nsubs if s not in ninst]
    print("\n    'none': [%s],"
          % ', '.join("'%s'" % s for s in no_site))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(__doc__)
        raise SystemExit(2)
    main(sys.argv[1], sys.argv[2])
