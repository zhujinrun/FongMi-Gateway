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

The annotation class itself survives: it is com/github/catvod/spider/merge/z0/b
(value() -> String, alternate() -> String[]), and merge/b1/z still calls
field.getAnnotation() on it. Only the *usages* were stripped, which is exactly
what this script puts back.

Where the names come from
-------------------------
Not from guesswork. Every entry below is backed by the bytecode that builds the
object: the constructor that assigns the field, plus the call site that supplies
each argument, so the semantic name is pinned by the JSON key the value was read
from. Each mapping carries that evidence in a comment.

Usage:
    python gsonfields.py <in.jar> <out.jar>
"""

import io
import sys
import zipfile

from classfile import ClassFile

# The renamed SerializedName annotation.
ANNOTATION = 'Lcom/github/catvod/spider/merge/z0/b;'
ANNOTATION_CLASS = 'com/github/catvod/spider/merge/z0/b'

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

# class name -> {field: serialized name}, with the evidence for each mapping.
FIELDS = {
    # Class bean. Upstream com.github.catvod.bean.Class declares type_id,
    # type_name, type_flag in this order; merge/k/b's only constructor is
    # (String, String, String) assigning a, b, c in order, and MyPan calls it
    # with (id, name, "1").
    'com/github/catvod/spider/merge/k/b': {
        'a': 'type_id',
        'b': 'type_name',
        'c': 'type_flag',
    },
    # Vod bean. merge/k/k has 14 String fields (a–n) + 1 Style (o).
    # Four-arg constructor assigns b/c/d/e; the remaining fields are set via
    # one-letter setters whose targets were read from putfield opcodes:
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
    # m and n are the only two Strings without live evidence; they match
    # upstream Vod's remaining names type_name and action in declaration order.
    'com/github/catvod/spider/merge/k/k': {
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
    },
    # Style bean, nested inside Vod as field o. Upstream Style is
    # (String type, float ratio); merge/k/j is (String a, Float b) with the only
    # factory being a(float) that builds a ratio-only instance, and
    # AiNewJuTou serializes it as {"a":"list"} -- i.e. type="list".
    'com/github/catvod/spider/merge/k/j': {
        'a': 'type',
        'b': 'ratio',
    },
    # Result bean. merge/k/h carries the class/list/filters trio: MyPan's
    # homeContent assigns a = the Class list and c = a LinkedHashMap decoded
    # from the filters JSON, and WexAiWenCai assigns a = classes and b = the
    # Vod list. m/n are ints and are left alone -- they default to 0 and the
    # player ignores unknown keys.
    'com/github/catvod/spider/merge/k/h': {
        'a': 'class',
        'b': 'list',
        'c': 'filters',
    },
}


def patch(data):
    """Return new class bytes, or None when the class needs nothing."""
    cf = ClassFile(data)
    if cf.name == ANNOTATION_CLASS:
        return cf.to_bytes(class_annotations=ANNOTATION_META)
    fields = FIELDS.get(cf.name)
    if not fields:
        return None
    # alternate() must be supplied explicitly: its AnnotationDefault was stripped
    # along with everything else, so leaving it out makes Gson throw
    # IncompleteAnnotationException the moment it reads the annotation.
    annotations = {
        name: [(ANNOTATION, [('value', 's', serialized), ('alternate', 'array', [])])]
        for name, serialized in fields.items()
    }
    return cf.to_bytes(field_annotations=annotations)


def main(src, dst):
    patched = []
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename.endswith('.class'):
                new = patch(data)
                if new is not None and new != data:
                    patched.append(item.filename)
                    data = new
            zout.writestr(item, data)
    for name in patched:
        print('  annotated %s' % name)
    print('%d class(es) annotated' % len(patched))
    missing = [k for k in FIELDS if k + '.class' not in patched]
    if missing:
        print('WARNING: never patched: ' + ', '.join(missing))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], sys.argv[2])
