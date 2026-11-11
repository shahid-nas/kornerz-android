#!/usr/bin/env python3
"""Verify a generated Android Vector Drawable renders identically to its iOS SVG.

The Vector Drawable is converted back into an equivalent SVG (same path data,
same fill colours, same viewport) and both are rasterised with cairosvg. The
two PNGs are then compared pixel by pixel.

Usage:
    python3 design/verify_vector_drawable.py <source.svg> <drawable.xml>
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET

import cairosvg
from PIL import Image, ImageChops

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def android_color_to_svg(value: str) -> tuple[str, float]:
    """Split an Android ARGB colour into an SVG colour and its opacity."""
    if not value.startswith("#") or len(value) != 9:
        return value, 1.0
    alpha = int(value[1:3], 16) / 255.0
    return f"#{value[3:]}", alpha


def drawable_to_svg(xml_path: str) -> str:
    root = ET.parse(xml_path).getroot()
    width = root.get(f"{ANDROID_NS}width", "24dp").replace("dp", "")
    height = root.get(f"{ANDROID_NS}height", "24dp").replace("dp", "")
    vw = root.get(f"{ANDROID_NS}viewportWidth", width)
    vh = root.get(f"{ANDROID_NS}viewportHeight", height)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {vw} {vh}">'
    ]

    def emit(node: ET.Element, group_opacity: float) -> None:
        for child in node:
            if child.tag == "group":
                alpha = float(child.get(f"{ANDROID_NS}alpha", "1"))
                # Reproduce SVG group semantics: the group is composited as a
                # unit and then faded once. Fading each path individually
                # would double-composite overlapping areas.
                parts.append(f'  <g opacity="{round(alpha, 6)}">')
                emit(child, 1.0)
                parts.append("  </g>")
                continue
            if child.tag != "path":
                continue

            d = child.get(f"{ANDROID_NS}pathData", "")
            fill_raw = child.get(f"{ANDROID_NS}fillColor", "none")
            fill, fill_opacity = android_color_to_svg(fill_raw)
            fill_alpha = float(child.get(f"{ANDROID_NS}fillAlpha", "1"))

            attrs = [f'd="{d}"', f'fill="{fill}"']
            if fill_opacity < 1.0:
                attrs.append(f'fill-opacity="{fill_opacity}"')
            if fill_alpha < 1.0:
                attrs.append(f'opacity="{fill_alpha}"')

            stroke_raw = child.get(f"{ANDROID_NS}strokeColor")
            if stroke_raw:
                stroke, stroke_opacity = android_color_to_svg(stroke_raw)
                stroke_alpha = float(child.get(f"{ANDROID_NS}strokeAlpha", "1"))
                attrs.append(f'stroke="{stroke}"')
                attrs.append(f'stroke-width="{child.get(f"{ANDROID_NS}strokeWidth", "1")}"')
                if stroke_opacity < 1.0:
                    attrs.append(f'stroke-opacity="{stroke_opacity}"')
                if stroke_alpha < 1.0:
                    attrs.append(f'opacity="{stroke_alpha}"')
                cap = child.get(f"{ANDROID_NS}strokeLineCap")
                if cap:
                    attrs.append(f'stroke-linecap="{cap}"')
                join = child.get(f"{ANDROID_NS}strokeLineJoin")
                if join:
                    attrs.append(f'stroke-linejoin="{join}"')

            parts.append(f"  <path {' '.join(attrs)}/>")

    emit(root, 1.0)
    parts.append("</svg>")
    return "\n".join(parts)


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: verify_vector_drawable.py <source.svg> <drawable.xml>", file=sys.stderr)
        return 2

    svg_path, xml_path = argv
    size = 1000

    cairosvg.svg2png(url=svg_path, write_to="/tmp/_verify_source.png", output_width=size)
    svg_rebuilt = drawable_to_svg(xml_path)
    with open("/tmp/_verify_drawable.svg", "w", encoding="utf-8") as handle:
        handle.write(svg_rebuilt)
    cairosvg.svg2png(
        url="/tmp/_verify_drawable.svg",
        write_to="/tmp/_verify_drawable.png",
        output_width=size,
    )

    src = Image.open("/tmp/_verify_source.png").convert("RGBA")
    dst = Image.open("/tmp/_verify_drawable.png").convert("RGBA")
    if src.size != dst.size:
        print(f"FAIL: size mismatch {src.size} vs {dst.size}")
        return 1

    diff = ImageChops.difference(src, dst)
    bbox = diff.getbbox()
    extrema = diff.getextrema()
    max_delta = max(channel[1] for channel in extrema)

    # Count pixels that differ at all (channel-wise).
    differing = sum(1 for px in diff.getdata() if px[:3] != (0, 0, 0))
    total = src.size[0] * src.size[1]

    print(f"source  : {svg_path}")
    print(f"drawable: {xml_path}")
    print(f"size    : {src.size[0]}x{src.size[1]}")
    print(f"max channel delta : {max_delta}")
    print(f"differing pixels  : {differing} / {total} ({100.0 * differing / total:.4f}%)")
    if bbox:
        print(f"diff bbox: {bbox}")
        src.crop(bbox).save("/tmp/_verify_diff_src.png")
        dst.crop(bbox).save("/tmp/_verify_diff_dst.png")

    # A tolerance of a couple of 8-bit alpha steps absorbs rasteriser
    # anti-aliasing rounding on hairline strokes; anything larger is a real
    # difference in geometry or colour.
    tolerance = 2
    if max_delta <= tolerance:
        print(f"RESULT: MATCH (within +/-{tolerance} alpha rounding)")
        return 0
    print("RESULT: DIFFERS")
    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
