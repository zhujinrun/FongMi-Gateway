#!/usr/bin/env python3
"""Restore the generic superclass type argument of Gson TypeToken subclasses.

Why this is needed
------------------
The payload is R8 output. R8 strips the `Signature` attribute unless
`-keepattributes Signature` is set, so every `new TypeToken<List<Item>>(){}`
loses the `<List<Item>>` part. Gson's TypeToken constructor then reads
`getClass().getGenericSuperclass()`, gets a raw `TypeToken` class instead of a
`ParameterizedType`, and throws:

    IllegalStateException: TypeToken must be created with a type argument ...

Guessing is not required for most of these: the payload is a merge of open
source CatVod spiders, and each such anonymous class is used by exactly one
bean helper that deserializes a JSON array into `List<Self>` (upstream writes
`TypeToken.getParameterized(List.class, Item.class)`). The table below records
the evidence for every entry instead of trusting the pattern blindly.

How the type argument was established
-------------------------------------
* `List<Self>` entries: the bean hosting the helper is cast by its consumer
  (e.g. `AList` casts to `merge/l/b`), which is only possible if the list
  elements really are instances of that bean.
* `l0/*`: `l0/d.f()` casts elements of the list returned by `l0/d.i()` to
  `merge/l0/g`; `l0/d.e()` does the same after its own deserialization.
* `k/f`, `k/g`: the result is stored into `k/h.c` (`LinkedHashMap`) and `c` is
  never read -- it only gets re-serialized. `Object` values keep the embedded
  JSON structure intact, which is the only thing that matters for a pass
  through field.

Classes with no instantiation site at all (`k/a`, `k/c`, `q/b`) are left alone:
they cannot throw because nothing ever constructs them.

Usage
-----
    python gsonparams.py <in.jar> <out.jar> [--dry-run]

The output jar is byte-identical to the input except for the added attributes.
"""

import io
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from classfile import ClassFile          # noqa: E402

TOKEN = 'com/github/catvod/spider/merge/f1/a'

# TypeToken's own class-level declaration lost its type parameter too. Without
# it the JVM rejects the subclass signatures: reflection verifies that the
# number of actual type arguments matches the raw superclass' formal ones and
# throws `MalformedParameterizedTypeException: 0 formal argument(s) 1 actual`.
TOKEN_SIGNATURE = '<T:Ljava/lang/Object;>Ljava/lang/Object;'

# anon class -> (type argument, why)
SIGNATURES = {
    'merge/k/f': (
        'Ljava/util/LinkedHashMap<Ljava/lang/String;Ljava/lang/Object;>;',
        'k/h.u and k/h.w store the parse result into k/h.c (LinkedHashMap) and '
        'that field is only ever re-serialized, so structural pass-through is '
        'the only requirement'),
    'merge/k/g': (
        'Ljava/util/LinkedHashMap<Ljava/lang/String;Ljava/lang/Object;>;',
        'same field as k/f, via k/h.q and k/h.s'),
    'merge/l/a': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/l/b;>;',
        'AList casts the elements returned by l/b.a(String) to merge/l/b'),
    'merge/l/c': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/l/d;>;',
        'AList and AList$Job cast the elements returned by l/d.a(String) to merge/l/d'),
    'merge/l0/a': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/l0/g;>;',
        'l0/d.e casts the deserialized elements to merge/l0/g'),
    'merge/l0/b': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/l0/g;>;',
        'l0/d.f casts the elements returned by l0/d.i to merge/l0/g'),
    'merge/n/f': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/n/g;>;',
        'Bili casts the elements returned by n/g.a to merge/n/g'),
    'merge/o/d': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/o/f;>;',
        'merge/h/c, merge/f/a and merge/g/w cast to merge/o/f'),
    'merge/r/a': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/r/b;>;',
        'Market casts the elements returned by r/b.a(String) to merge/r/b'),
    'merge/s/a': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/s/b;>;',
        'Samba casts the elements returned by s/b.a(String) to merge/s/b'),
    'merge/t/a': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/t/b;>;',
        'WebDAV casts the elements returned by t/b.a(String) to merge/t/b'),
    'merge/v/b': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/v/c;>;',
        'XtreamCode casts the elements returned by v/c.a(String) to merge/v/c'),
    'merge/v/d': (
        'Ljava/util/List<Lcom/github/catvod/spider/merge/v/e;>;',
        'XtreamCode casts the elements returned by v/e.a(String) to merge/v/e'),
}

# Never instantiated anywhere in the payload; nothing to repair.
NO_INSTANTIATION = ['merge/k/a', 'merge/k/c', 'merge/q/b']


def full(name):
    return name if name.startswith('com/') else 'com/github/catvod/spider/' + name


def patch(data):
    """Return (new_bytes, applied_signature)."""
    cf = ClassFile(data)
    if cf.name == TOKEN:
        return cf.to_bytes(add_signature=TOKEN_SIGNATURE), TOKEN_SIGNATURE
    simple = cf.name.replace('com/github/catvod/spider/', '')
    if cf.supername != TOKEN:
        return data, None
    entry = SIGNATURES.get(simple)
    if entry is None:
        return data, None
    arg, _why = entry
    signature = 'L%s<%s>;' % (TOKEN, arg)
    return cf.to_bytes(add_signature=signature), signature


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    src, dst = argv[1], argv[2]
    dry = '--dry-run' in argv

    patched = []
    with zipfile.ZipFile(src) as zin:
        out = io.BytesIO()
        with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                body = zin.read(item.filename)
                if item.filename.endswith('.class'):
                    body2, sig = patch(body)
                    if sig:
                        patched.append((item.filename, sig))
                        body = body2
                zout.writestr(item, body)
        data = out.getvalue()

    for name, sig in patched:
        print('signed %-56s %s' % (name, sig))
    print('patched %d class(es)' % len(patched))

    seen = {name[:-len('.class')] for name, _ in patched}
    missing = [k for k in SIGNATURES if full(k) not in seen]
    if missing:
        print('WARNING: never found in jar: ' + ', '.join(missing))

    if not dry:
        with open(dst, 'wb') as f:
            f.write(data)
        print('wrote %s (%d B)' % (dst, len(data)))
    return 0


if __name__ == '__main__':
    raise SystemExit(main(sys.argv))
