#!/usr/bin/env python3
"""Restore @SerializedName on the R8-renamed bean fields.

Why this is needed
------------------
R8 rewrote every bean field to a single letter and, because nothing in the
payload reads the annotation at compile time, dropped the annotation as well.
Gson's ReflectiveTypeAdapterFactory then falls back to field.getName(), so a
spider that returns Result/Vod/Class objects serializes them as
{"a":[...],"c":{}} instead of {"class":[...],"filters":{}}. The player only
understands the standard CatVod keys, so those sources render as empty even
though their data is perfectly fine.

The annotation class itself survives; only the *usages* were stripped, which is
exactly what this script puts back.

Where the names come from
-------------------------
Not from guesswork. Every entry below is backed by the bytecode that builds the
object: the constructor that assigns the field, plus the call site that supplies
each argument, so the semantic name is pinned by the JSON key the value was read
from. Each mapping carries that evidence in a comment.

Generations
-----------
R8 renames internals per build, so both the annotation class and the bean class
names change. `GENERATIONS` is keyed by the @SerializedName annotation class and
the script detects the generation by scanning the jar for those entries. The
2026-11-12 entry reuses the same field->name mapping verbatim because its bean
layouts are field-for-field identical to the 2026-10-26 payload (same order, same
descriptors), which was verified before the mapping was copied.

Usage:
    python gsonfields.py <in.jar> <out.jar>
"""

import io
import sys
import zipfile

from classfile import ClassFile

# R8 stripped the meta-annotations off the annotation type as well. Without
# @Retention(RUNTIME) the JDK treats the annotation as CLASS-retained and
# getDeclaredAnnotations() returns an empty array even though the field carries a
# well-formed RuntimeVisibleAnnotations attribute -- the injection silently does
# nothing, which is a nasty way to fail. @Target is restored too so the
# annotation stays legal on fields and methods.
ANNOTATION_META = [
    ('Ljava/lang/annotation/Retention;',
     [('value', 'e', ('Ljava/lang/annotation/RetentionPolicy;', 'RUNTIME'))]),
    ('Ljava/lang/annotation/Target;',
     [('value', 'array', [
         ('e', ('Ljava/lang/annotation/ElementType;', 'FIELD')),
         ('e', ('Ljava/lang/annotation/ElementType;', 'METHOD')),
     ])]),
]

# Class bean (type_id / type_name / type_flag) -- 3 Strings, one 3-arg ctor.
_CLASS_FIELDS = {'a': 'type_id', 'b': 'type_name', 'c': 'type_flag'}

# Vod bean. 14 String fields (a-n) + 1 Style (o). Four-arg constructor assigns
# b/c/d/e; the remaining fields are set via one-letter setters whose targets were
# read from putfield opcodes:
#   d→a  e→h  f→g  g→j  h→i  i→b  j→c  k→d  l→k  m→l  n→e  o→m  p→f
# Semantic names come from the live detail response for 花卷 id=18879
# (嫁金钗), whose values are self-describing:
#   a="剧情,爱情,古装"  → vod_tag
#   f="2026"           → vod_year
#   g="大陆"            → vod_area
#   h="田曦薇,闫桉,…"   → vod_actor
#   i="杨龙"            → vod_director
#   j="阿丑原是街上…"   → vod_content
#   k="夸克原画$$$…"    → vod_play_from
#   l="[1.7GB]E01.mkv"  → vod_play_url
# m and n are the only two Strings without live evidence; they match upstream
# Vod's remaining names type_name and action in declaration order.
_VOD_FIELDS = {
    'a': 'vod_tag',
    'b': 'vod_id',
    'c': 'vod_name',
    'd': 'vod_pic',
    'e': 'vod_remarks',
    'f': 'vod_year',
    'g': 'vod_area',
    'h': 'vod_actor',
    'i': 'vod_director',
    'j': 'vod_content',
    'k': 'vod_play_from',
    'l': 'vod_play_url',
    'm': 'type_name',
    'n': 'action',
    'o': 'style',
}

# Style bean, nested inside Vod as field o. Upstream Style is
# (String type, float ratio); the payload's is (String a, Float b) with the only
# factory being a(float) that builds a ratio-only instance, and AiNewJuTou
# serializes it as {"a":"list"} -- i.e. type="list".
_STYLE_FIELDS = {'a': 'type', 'b': 'ratio'}

# Result bean: the class/list/filters trio. It carries a = the Class list,
# b = the Vod list, c = a LinkedHashMap decoded from the filters JSON (assigned
# by the two pass-through TypeToken helpers). m/n are ints and are left alone --
# they default to 0 and the player ignores unknown keys.
_RESULT_FIELDS = {'a': 'class', 'b': 'list', 'c': 'filters'}

GENERATIONS = [
    {   # 2026-10-26 payload
        'annotation': 'com/github/catvod/spider/merge/z0/b',
        'fields': {
            'com/github/catvod/spider/merge/k/b': _CLASS_FIELDS,
            'com/github/catvod/spider/merge/k/k': _VOD_FIELDS,
            'com/github/catvod/spider/merge/k/j': _STYLE_FIELDS,
            'com/github/catvod/spider/merge/k/h': _RESULT_FIELDS,
        },
    },
    {   # 2026-11-12 payload (source sha256 9848e4d7...), layouts identical
        'annotation': 'com/github/catvod/spider/merge/a1/b',
        'fields': {
            'com/github/catvod/spider/merge/l/b': _CLASS_FIELDS,
            'com/github/catvod/spider/merge/l/k': _VOD_FIELDS,
            'com/github/catvod/spider/merge/l/j': _STYLE_FIELDS,
            'com/github/catvod/spider/merge/l/h': _RESULT_FIELDS,
        },
    },
]

BY_ANNOTATION = {g['annotation']: g for g in GENERATIONS}


def detect(zin):
    """Pick the generation by structure, not by name.

    Name presence is not a safe test: a build can contain an unrelated class
    whose name coincides with another generation's annotation. The serialized
    name annotation is the unique interface declaring both value()->String and
    alternate()->String[].
    """
    cands = []
    for item in zin.infolist():
        if not item.filename.endswith('.class'):
            continue
        cf = ClassFile(zin.read(item.filename))
        if not cf.access & 0x2000:                       # ACC_ANNOTATION
            continue
        _fields, methods, _attrs = cf.parse_members()
        descs = {cf.utf8(m[2]) for m in methods}
        if '()Ljava/lang/String;' in descs and '()[Ljava/lang/String;' in descs:
            cands.append(cf.name)
    hits = [g for g in GENERATIONS if g['annotation'] in cands]
    if len(hits) != 1:
        raise SystemExit('cannot tell the payload generation apart: %s / %s'
                         % (cands, [g['annotation'] for g in GENERATIONS]))
    return hits[0]


def patch(data, gen):
    """Return new class bytes, or None when the class needs nothing."""
    cf = ClassFile(data)
    annotation = 'L%s;' % gen['annotation']
    if cf.name == gen['annotation']:
        return cf.to_bytes(class_annotations=ANNOTATION_META)
    fields = gen['fields'].get(cf.name)
    if not fields:
        return None
    # alternate() must be supplied explicitly: its AnnotationDefault was stripped
    # along with everything else, so leaving it out makes Gson throw
    # IncompleteAnnotationException the moment it reads the annotation.
    annotations = {
        name: [(annotation, [('value', 's', serialized), ('alternate', 'array', [])])]
        for name, serialized in fields.items()
    }
    return cf.to_bytes(field_annotations=annotations)


def main(src, dst):
    patched = []
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
        gen = detect(zin)
        print('generation: %s' % gen['annotation'])
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename.endswith('.class'):
                new = patch(data, gen)
                if new is not None and new != data:
                    patched.append(item.filename)
                    data = new
            zout.writestr(item, data)
    for name in patched:
        print('  annotated %s' % name)
    print('%d class(es) annotated' % len(patched))
    missing = [k for k in gen['fields'] if k + '.class' not in patched]
    if missing:
        print('WARNING: never patched: ' + ', '.join(missing))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], sys.argv[2])
