#!/usr/bin/env python3
"""Build the mod's Korean font from the system Noto Sans CJK variable collection.

The 26.3 client ships an empty ``unifont`` include, and the downloadable asset
pack is not guaranteed to be present on every install, so the mod carries its
own font. Noto Sans CJK is an OpenType/CFF collection; 26.3 loads TTF providers
through FreeType, which renders CFF outlines fine.

Output: src/main/resources/assets/hangulquest/font/hangul.ttf
"""

from __future__ import annotations

import argparse
import os
import sys

from fontTools import subset
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTCollection, TTFont
from fontTools.varLib.instancer import instantiateVariableFont

DEFAULT_SOURCE = "/usr/share/fonts/google-noto-sans-cjk-vf-fonts/NotoSansCJK-VF.ttc"

# Hangul syllables, jamo (modern/compatibility/extended), ASCII and the
# punctuation the mod's sentences use.
UNICODE_RANGES = [
    (0x0020, 0x007E),  # ASCII
    (0x00A0, 0x00FF),  # Latin-1 punctuation/symbols
    (0x1100, 0x11FF),  # Hangul Jamo
    (0x2000, 0x206F),  # general punctuation (…, –, “ ”)
    (0x3000, 0x303F),  # CJK punctuation
    (0x3130, 0x318F),  # Hangul compatibility jamo
    (0xAC00, 0xD7A3),  # Hangul syllables
    (0xA960, 0xA97F),  # Hangul Jamo Extended-A
    (0xD7B0, 0xD7FF),  # Hangul Jamo Extended-B
    (0xFF00, 0xFFEF),  # halfwidth/fullwidth forms
]


def codepoints() -> set[int]:
    result = set()
    for start, end in UNICODE_RANGES:
        result.update(range(start, end + 1))
    return result


def find_korean_face(path: str) -> tuple[int, str]:
    collection = TTCollection(path, lazy=True)
    fallback = (0, "?")
    for index, font in enumerate(collection.fonts):
        family = font["name"].getDebugName(1) or ""
        if "KR" in family:
            return index, family
        if "CJK" in family:
            fallback = (index, family)
    return fallback


def to_truetype(font: TTFont) -> TTFont:
    """26.3's TTF provider uses FreeType but rejects CFF outlines; convert to glyf."""
    glyph_set = font.getGlyphSet()
    order = font.getGlyphOrder()
    cmap = font.getBestCmap()
    glyphs = {}
    for name in order:
        pen = TTGlyphPen(glyph_set)
        glyph_set[name].draw(Cu2QuPen(pen, max_err=0.6, reverse_direction=True))
        glyphs[name] = pen.glyph()

    metrics = {name: font["hmtx"][name] for name in order}
    hhea = font["hhea"]
    os2 = font["OS/2"]
    builder = FontBuilder(font["head"].unitsPerEm, isTTF=True)
    builder.setupGlyphOrder(order)
    builder.setupCharacterMap(cmap)
    builder.setupGlyf(glyphs)
    builder.setupHorizontalMetrics(metrics)
    builder.setupHorizontalHeader(ascent=hhea.ascent, descent=hhea.descent)
    builder.setupNameTable({
        "familyName": "Hangul Quest",
        "styleName": "Regular",
        "uniqueFontIdentifier": "HangulQuest-Regular",
        "fullName": "Hangul Quest Regular",
        "psName": "HangulQuest-Regular",
        "version": "Version 1.0",
    })
    builder.setupOS2(
        sTypoAscender=os2.sTypoAscender,
        sTypoDescender=os2.sTypoDescender,
        sTypoLineGap=os2.sTypoLineGap,
        usWinAscent=os2.usWinAscent,
        usWinDescent=os2.usWinDescent,
        sxHeight=getattr(os2, "sxHeight", 0),
        sCapHeight=getattr(os2, "sCapHeight", 0),
    )
    builder.setupPost()
    return builder.font


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default=DEFAULT_SOURCE)
    parser.add_argument(
        "--out",
        default=os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "src/main/resources/assets/hangulquest/font/hangul.ttf",
        ),
    )
    args = parser.parse_args()

    if not os.path.exists(args.source):
        print(f"font source missing: {args.source}", file=sys.stderr)
        return 1

    index, family = find_korean_face(args.source)
    print(f"using face {index} ({family}) from {args.source}")
    font: TTFont = TTFont(args.source, fontNumber=index)
    if "fvar" in font:
        font = instantiateVariableFont(font, {"wght": 400}, inplace=True)
        print("instantiated variable font at wght=400")

    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.notdef_outline = True
    options.recalc_bounds = True
    options.drop_tables += ["DSIG", "VORG"]

    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=codepoints())
    subsetter.subset(font)

    if "CFF " in font or "CFF2" in font:
        print("converting CFF outlines to TrueType (glyf)")
        font = to_truetype(font)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    font.save(args.out)
    size = os.path.getsize(args.out)
    print(f"wrote {args.out} ({size / 1024 / 1024:.2f} MiB)")
    if size > 8 * 1024 * 1024:
        print("warning: font exceeds 8 MiB; consider trimming the subset", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
