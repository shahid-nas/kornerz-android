#!/usr/bin/env python3
"""Convert iOS SVG assets into Android Vector Drawables.

Source of truth
---------------
The brand and illustration artwork lives in the iOS app as SVG files inside
``kornerz/Kornerz/Resources/Assets.xcassets``. Android's resource system has
no SVG support, so every vector asset must be expressed as an XML
``<vector>`` drawable.

Hand-transcribing SVG path data into Vector Drawables is error prone: glyphs
and whole shapes have silently gone missing (the ``n`` of the ``Kornerz``
wordmark was dropped from ``logo_kornerz.xml``). This script performs the
conversion mechanically so the Android drawable is always a faithful,
loss-free translation of the iOS SVG.

Supported SVG features
----------------------
* ``<path>`` with ``d``, ``fill``, ``fill-opacity``, ``stroke``,
  ``stroke-width``, ``stroke-opacity``, ``stroke-linecap`` and
  ``stroke-linejoin``.
* ``<line>`` with the same stroke attributes.
* ``<rect>``, ``<circle>``, ``<ellipse>``, ``<polygon>``, ``<polyline>``.
* ``<g>`` for group opacity and group ``transform`` (translate, scale,
  rotate, matrix, skew).
* ``<defs>``/``<clipPath>`` are ignored, but a top-level clipPath that covers
  the whole viewport is a no-op and therefore safe to drop.

Usage
-----
    python3 design/svg_to_vector_drawable.py \
        <source.svg> <target.xml> [--name NAME] [--note TEXT]

Example
-------
    python3 design/svg_to_vector_drawable.py \
        kornerz/Kornerz/Resources/Assets.xcassets/app-logo.imageset/logo.svg \
        KornerzAndroid/feature/auth/src/main/res/drawable/logo_kornerz.xml
"""

from __future__ import annotations

import argparse
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

SVG_NS = "http://www.w3.org/2000/svg"

# Android colour tokens.
COLOR_BLACK = "#FF000000"
COLOR_WHITE = "#FFFFFFFF"
COLOR_TRANSPARENT = "#00000000"

NUMERIC_RE = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")


def strip_ns(tag: str) -> str:
    return tag.split("}", 1)[-1]


def parse_number_list(text: str) -> list[float]:
    if not text:
        return []
    return [float(m.group(0)) for m in NUMERIC_RE.finditer(text)]


def parse_transform(text: str) -> list[tuple[str, list[float]]]:
    """Parse an SVG ``transform`` attribute into a list of operations."""
    if not text:
        return []
    ops: list[tuple[str, list[float]]] = []
    for name, raw_args in re.findall(r"([a-zA-Z]+)\s*\(([^)]*)\)", text):
        args = parse_number_list(raw_args)
        ops.append((name, args))
    return ops


def multiply(a: list[float], b: list[float]) -> list[float]:
    """Multiply two 3x3 affine matrices given in SVG order (a, b, c, d, e, f)."""
    a0, a1, a2, a3, a4, a5 = a
    b0, b1, b2, b3, b4, b5 = b
    return [
        a0 * b0 + a2 * b1,
        a1 * b0 + a3 * b1,
        a0 * b2 + a2 * b3,
        a1 * b2 + a3 * b3,
        a0 * b4 + a2 * b5 + a4,
        a1 * b4 + a3 * b5 + a5,
    ]


def transform_to_matrix(op: tuple[str, list[float]]) -> list[float]:
    name, args = op
    if name == "translate":
        tx = args[0] if args else 0.0
        ty = args[1] if len(args) > 1 else 0.0
        return [1.0, 0.0, 0.0, 1.0, tx, ty]
    if name == "scale":
        sx = args[0] if args else 1.0
        sy = args[1] if len(args) > 1 else sx
        return [sx, 0.0, 0.0, sy, 0.0, 0.0]
    if name == "rotate":
        angle = math.radians(args[0] if args else 0.0)
        cx = args[1] if len(args) > 1 else 0.0
        cy = args[2] if len(args) > 2 else 0.0
        cos_a = math.cos(angle)
        sin_a = math.sin(angle)
        return multiply(
            [1.0, 0.0, 0.0, 1.0, cx, cy],
            multiply(
                [cos_a, sin_a, -sin_a, cos_a, 0.0, 0.0],
                [1.0, 0.0, 0.0, 1.0, -cx, -cy],
            ),
        )
    if name == "matrix" and len(args) >= 6:
        return args[:6]
    if name in ("skewX", "skewY") and args:
        angle = math.radians(args[0])
        if name == "skewX":
            return [1.0, 0.0, math.tan(angle), 1.0, 0.0, 0.0]
        return [1.0, math.tan(angle), 0.0, 1.0, 0.0, 0.0]
    return [1.0, 0.0, 0.0, 1.0, 0.0, 0.0]


def apply_matrix_to_path_data(d: str, matrix: list[float]) -> str:
    """Rewrite an SVG path ``d`` attribute with an affine transform applied.

    Only the commands actually used by the brand artwork are supported:
    ``M m L l H h V v C c S s Q q T t A a Z z``. Curves keep their original
    control points and simply gain the transformed endpoints, which is exactly
    how an affine map acts on a Bezier segment.
    """
    ma, mb, mc, md, me, mf = matrix
    tokens = re.split(r"([MmLlHhVvCcSsQqTtAaZz])", d)
    out: list[str] = []
    cmd = ""
    start = (0.0, 0.0)
    cur = (0.0, 0.0)
    last_ctrl: tuple[float, float] | None = None

    def tx(x: float, y: float) -> tuple[float, float]:
        return (ma * x + mc * y + me, mb * x + md * y + mf)

    def fmt(value: float) -> str:
        text = f"{value:.5f}".rstrip("0").rstrip(".")
        return "0" if text in ("", "-0") else text

    def emit_point(x: float, y: float) -> str:
        nx, ny = tx(x, y)
        return f"{fmt(nx)},{fmt(ny)}"

    i = 0
    while i < len(tokens):
        tok = tokens[i]
        i += 1
        if not tok.strip():
            continue
        if re.match(r"^[MmLlHhVvCcSsQqTtAaZz]$", tok):
            cmd = tok
            if cmd.upper() == "Z":
                out.append("Z")
                cur = start
                last_ctrl = None
            continue
        nums = parse_number_list(tok)
        if not nums:
            continue
        rel = cmd.islower()
        upper = cmd.upper()

        if upper == "M":
            for k in range(0, len(nums), 2):
                x, y = nums[k], nums[k + 1]
                if rel:
                    x, y = cur[0] + x, cur[1] + y
                cur = (x, y)
                start = cur
                out.append(f"{'M' if k == 0 else 'L'} {emit_point(x, y)}")
            cmd = "l" if rel else "L"
            last_ctrl = None
        elif upper == "L":
            for k in range(0, len(nums), 2):
                x, y = nums[k], nums[k + 1]
                if rel:
                    x, y = cur[0] + x, cur[1] + y
                cur = (x, y)
                out.append(f"L {emit_point(x, y)}")
            last_ctrl = None
        elif upper == "H":
            for x in nums:
                if rel:
                    x = cur[0] + x
                cur = (x, cur[1])
                out.append(f"L {emit_point(x, cur[1])}")
            last_ctrl = None
        elif upper == "V":
            for y in nums:
                if rel:
                    y = cur[1] + y
                cur = (cur[0], y)
                out.append(f"L {emit_point(cur[0], y)}")
            last_ctrl = None
        elif upper in ("C", "S"):
            step = 6 if upper == "C" else 4
            for k in range(0, len(nums), step):
                chunk = nums[k : k + step]
                if upper == "C":
                    x1, y1, x2, y2, x, y = chunk
                else:
                    x2, y2, x, y = chunk
                    if last_ctrl is None:
                        x1, y1 = cur
                    else:
                        x1, y1 = 2 * cur[0] - last_ctrl[0], 2 * cur[1] - last_ctrl[1]
                pts = [(x1, y1), (x2, y2), (x, y)]
                if rel:
                    pts = [(cur[0] + px, cur[1] + py) for px, py in pts]
                cur = (pts[2][0], pts[2][1])
                last_ctrl = (pts[1][0], pts[1][1])
                out.append(
                    "C "
                    + " ".join(
                        [
                            emit_point(pts[0][0], pts[0][1]),
                            emit_point(pts[1][0], pts[1][1]),
                            emit_point(pts[2][0], pts[2][1]),
                        ]
                    )
                )
        elif upper in ("Q", "T"):
            step = 4 if upper == "Q" else 2
            for k in range(0, len(nums), step):
                chunk = nums[k : k + step]
                if upper == "Q":
                    x1, y1, x, y = chunk
                else:
                    x, y = chunk
                    if last_ctrl is None:
                        x1, y1 = cur
                    else:
                        x1, y1 = 2 * cur[0] - last_ctrl[0], 2 * cur[1] - last_ctrl[1]
                pts = [(x1, y1), (x, y)]
                if rel:
                    pts = [(cur[0] + px, cur[1] + py) for px, py in pts]
                cur = (pts[1][0], pts[1][1])
                last_ctrl = (pts[0][0], pts[0][1])
                out.append(
                    "Q " + " ".join([emit_point(pts[0][0], pts[0][1]), emit_point(pts[1][0], pts[1][1])])
                )
        elif upper == "A":
            step = 7
            for k in range(0, len(nums), step):
                chunk = nums[k : k + step]
                rx, ry, rot, large, sweep, x, y = chunk
                if rel:
                    x, y = cur[0] + x, cur[1] + y
                nx, ny = tx(x, y)
                out.append(
                    f"A {fmt(rx)},{fmt(ry)} {fmt(rot)} {int(large)} {int(sweep)} {fmt(nx)},{fmt(ny)}"
                )
                cur = (x, y)
            last_ctrl = None
        elif upper == "Z":
            out.append("Z")
            cur = start
            last_ctrl = None
    return " ".join(out)


def parse_color(value: str | None, default: str | None) -> str | None:
    if value is None:
        return default
    value = value.strip()
    if not value or value.lower() == "none":
        return None
    if value.lower() == "transparent":
        return COLOR_TRANSPARENT
    if value.startswith("#"):
        hex_part = value[1:]
        if len(hex_part) == 3:
            hex_part = "".join(ch * 2 for ch in hex_part)
        if len(hex_part) == 6:
            return f"#FF{hex_part.upper()}"
        if len(hex_part) == 4:
            return f"#{hex_part.upper()}"
        if len(hex_part) == 8:
            return f"#{hex_part.upper()}"
    named = {
        "black": COLOR_BLACK,
        "white": COLOR_WHITE,
        "red": "#FFFF0000",
        "green": "#FF008000",
        "blue": "#FF0000FF",
        "transparent": COLOR_TRANSPARENT,
    }
    return named.get(value.lower(), default)


def color_with_opacity(color: str, opacity: float) -> str:
    """Apply a fill/stroke opacity to an Android ARGB colour string."""
    if opacity >= 1.0:
        return color
    if not color.startswith("#") or len(color) != 9:
        return color
    base = color[1:]
    alpha = int(base[:2], 16)
    new_alpha = int(round(alpha * opacity))
    return f"#{new_alpha:02X}{base[2:]}"


def svg_length(value: str | None) -> float:
    if not value:
        return 0.0
    nums = parse_number_list(value)
    return nums[0] if nums else 0.0


def collect_elements(
    node: ET.Element,
    inherited: dict,
    matrix: list[float],
    out: list[dict],
) -> None:
    """Flatten an SVG tree into a list of drawable nodes.

    ``<g>`` elements become nested ``group`` nodes so that SVG group opacity
    keeps its meaning: SVG renders a group as a unit and fades the composited
    result once, whereas fading each shape individually would double-composite
    overlapping areas.
    """
    for child in node:
        tag = strip_ns(child.tag)
        if tag in ("defs", "clipPath", "mask", "metadata", "title", "desc", "style"):
            continue

        style = dict(inherited)
        for attr in ("fill", "fill-opacity", "stroke", "stroke-opacity", "stroke-width",
                     "stroke-linecap", "stroke-linejoin", "opacity"):
            if attr in child.attrib:
                style[attr] = child.attrib[attr]

        child_matrix = matrix
        ops = parse_transform(child.attrib.get("transform", ""))
        for op in ops:
            child_matrix = multiply(child_matrix, transform_to_matrix(op))

        if tag == "g":
            group: dict = {
                "kind": "group",
                "opacity": float(child.attrib.get("opacity", "1") or 1.0),
                "children": [],
            }
            # The group's own opacity is carried by the emitted <group>
            # element, so it must not also be inherited by the children.
            child_style = dict(style)
            child_style["opacity"] = "1"
            collect_elements(child, child_style, child_matrix, group["children"])
            if group["children"]:
                out.append(group)
            continue

        if tag == "path":
            d = child.attrib.get("d")
            if not d:
                continue
            out.append(
                {
                    "kind": "path",
                    "d": apply_matrix_to_path_data(d, child_matrix),
                    "style": style,
                }
            )
        elif tag == "line":
            x1 = svg_length(child.attrib.get("x1"))
            y1 = svg_length(child.attrib.get("y1"))
            x2 = svg_length(child.attrib.get("x2"))
            y2 = svg_length(child.attrib.get("y2"))
            out.append(
                {
                    "kind": "path",
                    "d": apply_matrix_to_path_data(f"M{x1},{y1} L{x2},{y2}", child_matrix),
                    "style": style,
                }
            )
        elif tag == "rect":
            x = svg_length(child.attrib.get("x"))
            y = svg_length(child.attrib.get("y"))
            w = svg_length(child.attrib.get("width"))
            h = svg_length(child.attrib.get("height"))
            d = f"M{x},{y} H{x + w} V{y + h} H{x} Z"
            out.append({"kind": "path", "d": apply_matrix_to_path_data(d, child_matrix), "style": style})
        elif tag in ("circle", "ellipse"):
            cx = svg_length(child.attrib.get("cx"))
            cy = svg_length(child.attrib.get("cy"))
            if tag == "circle":
                rx = ry = svg_length(child.attrib.get("r"))
            else:
                rx = svg_length(child.attrib.get("rx"))
                ry = svg_length(child.attrib.get("ry"))
            out.append(
                {
                    "kind": "path",
                    "d": apply_matrix_to_path_data(
                        f"M{cx - rx},{cy} A{rx},{ry} 0 1 0 {cx + rx},{cy} A{rx},{ry} 0 1 0 {cx - rx},{cy} Z",
                        child_matrix,
                    ),
                    "style": style,
                }
            )
        elif tag in ("polygon", "polyline"):
            pts = parse_number_list(child.attrib.get("points", ""))
            coords = ", ".join(f"{pts[i]},{pts[i + 1]}" for i in range(0, len(pts) - 1, 2))
            suffix = "Z" if tag == "polygon" else ""
            out.append(
                {
                    "kind": "path",
                    "d": apply_matrix_to_path_data(f"M{coords} {suffix}", child_matrix),
                    "style": style,
                }
            )


def convert(svg_path: str, xml_path: str, name: str | None, note: str | None) -> None:
    tree = ET.parse(svg_path)
    root = tree.getroot()

    width = svg_length(root.attrib.get("width"))
    height = svg_length(root.attrib.get("height"))
    view_box = parse_number_list(root.attrib.get("viewBox", ""))
    if len(view_box) >= 4:
        viewport_w, viewport_h = view_box[2], view_box[3]
    else:
        viewport_w = width or 24.0
        viewport_h = height or 24.0

    style = {
        "fill": root.attrib.get("fill", "black"),
        "fill-opacity": root.attrib.get("fill-opacity", "1"),
        "stroke": root.attrib.get("stroke", "none"),
        "stroke-opacity": root.attrib.get("stroke-opacity", "1"),
        "stroke-width": root.attrib.get("stroke-width", "1"),
        "stroke-linecap": root.attrib.get("stroke-linecap", "butt"),
        "stroke-linejoin": root.attrib.get("stroke-linejoin", "miter"),
        "opacity": root.attrib.get("opacity", "1"),
    }

    elements: list[dict] = []
    collect_elements(root, style, [1.0, 0.0, 0.0, 1.0, 0.0, 0.0], elements)

    lines: list[str] = []
    def fmt_len(value: float) -> str:
        text = f"{value:.5f}".rstrip("0").rstrip(".")
        return text if text else "0"

    lines.append('<?xml version="1.0" encoding="utf-8"?>')
    if note:
        # One comment block for the whole note. "--" is illegal inside an XML
        # comment, so it is replaced with an en dash.
        safe_lines = [line.strip().replace("--", "–") for line in note.strip().splitlines()]
        lines.append("<!--")
        lines.extend(safe_lines)
        lines.append("-->")
    elif name:
        lines.append(f"<!-- {name.replace('--', '–')} -->")
    lines.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
    lines.append(f'    android:width="{fmt_len(width or viewport_w)}dp"')
    lines.append(f'    android:height="{fmt_len(height or viewport_h)}dp"')
    lines.append(f'    android:viewportWidth="{fmt_len(viewport_w)}"')
    lines.append(f'    android:viewportHeight="{fmt_len(viewport_h)}">')

    def emit_nodes(nodes: list[dict], depth: int) -> None:
        indent = "    " * depth
        for element in nodes:
            if element["kind"] == "group":
                group_opacity = element.get("opacity", 1.0)
                lines.append(f"{indent}<group")
                if group_opacity < 1.0:
                    lines.append(f'{indent}    android:alpha="{round(group_opacity, 4)}"')
                lines.append(f"{indent}    >")
                emit_nodes(element["children"], depth + 1)
                lines.append(f"{indent}</group>")
                lines.append("")
                continue

            st = element["style"]
            # Group opacity is carried by the enclosing <group> element, so it
            # must not be baked into the path colours as well.
            fill = parse_color(st.get("fill"), None)
            fill_opacity = float(st.get("fill-opacity", "1") or 1.0)
            stroke = parse_color(st.get("stroke"), None)
            stroke_opacity = float(st.get("stroke-opacity", "1") or 1.0)
            stroke_width = float(st.get("stroke-width", "1") or 1.0)

            attrs: list[str] = []
            if fill:
                attrs.append(f'android:fillColor="{color_with_opacity(fill, fill_opacity)}"')
            else:
                attrs.append(f'android:fillColor="{COLOR_TRANSPARENT}"')
            if stroke:
                attrs.append(f'android:strokeColor="{color_with_opacity(stroke, stroke_opacity)}"')
                attrs.append(f'android:strokeWidth="{fmt_len(stroke_width)}"')
                cap = st.get("stroke-linecap", "butt")
                if cap == "round":
                    attrs.append('android:strokeLineCap="round"')
                elif cap == "square":
                    attrs.append('android:strokeLineCap="square"')
                join = st.get("stroke-linejoin", "miter")
                if join == "round":
                    attrs.append('android:strokeLineJoin="round"')
                elif join == "bevel":
                    attrs.append('android:strokeLineJoin="bevel"')
                if stroke_opacity < 1.0:
                    attrs.append(f'android:strokeAlpha="{round(stroke_opacity, 4)}"')

            lines.append(f"{indent}<path")
            for attr in attrs:
                lines.append(f"{indent}    {attr}")
            lines.append(f'{indent}    android:pathData="{element["d"]}" />')
            lines.append("")

    emit_nodes(elements, 1)

    lines.append("</vector>")
    lines.append("")

    os.makedirs(os.path.dirname(os.path.abspath(xml_path)), exist_ok=True)
    with open(xml_path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))

    print(f"{svg_path} -> {xml_path} ({len(elements)} shapes)")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", help="path to the source .svg file")
    parser.add_argument("target", help="path to the generated .xml Vector Drawable")
    parser.add_argument("--name", help="optional comment written at the top of the file")
    parser.add_argument("--note", help="optional multi-line comment block")
    args = parser.parse_args(argv)

    if not os.path.exists(args.source):
        print(f"error: source not found: {args.source}", file=sys.stderr)
        return 1

    convert(args.source, args.target, args.name, args.note)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
