#!/usr/bin/env python3
"""Generate the Kornerz Android launcher icon set from the brand mark.

Source of truth
---------------
``design/output-onlinepngtools.png`` (1024x1024) is the artwork the design
team delivered; the iOS app uses the very same file as its ``AppIcon``. It
is an opaque, white-matted raster: the brand mark sits on a solid white
canvas rather than on transparency.

This script un-mattes that file into a transparent master and renders the
full Android launcher icon matrix from it:

* ``ic_launcher_foreground.png`` - adaptive foreground on a 108dp canvas with
  the mark scaled so that its farthest ink pixel lands inside the 66dp safe
  zone, so no launcher mask (circle, squircle, rounded square) can clip it;
* ``ic_launcher_monochrome.png`` - flat silhouette used by the Android 13+
  themed-icon feature;
* ``ic_launcher.png`` / ``ic_launcher_round.png`` - legacy fallbacks;
* ``playstore-icon.png`` - 512x512 Play listing icon, fully opaque.

Every artifact is written twice: once into ``design/android/`` (the
design-team hand-off copy) and once into
``KornerzAndroid/app/src/main/res/`` (the copy that ships in the APK). The
two trees must stay byte-identical.

Usage
-----
    python3 design/generate_android_icons.py

Requires Pillow (``python3 -m pip install Pillow``).
"""

from __future__ import annotations

import math
import os
import sys

from PIL import Image, ImageChops

# --------------------------------------------------------------------------
# Paths
# --------------------------------------------------------------------------

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SOURCE = os.path.join(HERE, "output-onlinepngtools.png")
DESIGN_OUT = os.path.join(HERE, "android")
RES_OUT = os.path.join(ROOT, "KornerzAndroid", "app", "src", "main", "res")

# --------------------------------------------------------------------------
# Brand palette (sampled from the source artwork)
# --------------------------------------------------------------------------

CHARCOAL = (0x2E, 0x2F, 0x3E)
PLUM = (0xAA, 0x52, 0x8B)
CORAL = (0xD9, 0x6C, 0x3C)

# The artwork is a blend of exactly three brand fills over white, so the
# un-matting step solves for the alpha that makes every channel agree.
BRAND_COLORS = (CHARCOAL, PLUM, CORAL)

BACKGROUND = (0xFF, 0xFF, 0xFF)

# --------------------------------------------------------------------------
# Geometry
# --------------------------------------------------------------------------

# Adaptive icon canvas is 108dp; only the centre 72dp is guaranteed visible
# and only the centre 66dp survives the most aggressive launcher masks.
ADAPTIVE_CANVAS_DP = 108
SAFE_ZONE_DP = 66

# Radius (in dp) the mark's farthest ink pixel is allowed to reach from the
# icon centre. Kept a hair inside the 33dp safe-zone radius so antialiased
# edges cannot bleed past the mask.
FOREGROUND_INK_RADIUS_DP = 32.0

# Legacy launcher icons are unmasked, so the mark can sit larger. The value
# is expressed as a fraction of the canvas half-width.
LEGACY_INK_RADIUS_RATIO = 0.37
ROUND_INK_RADIUS_RATIO = 0.35
PLAYSTORE_INK_RADIUS_RATIO = 0.36

PLAYSTORE_SIZE = 512

DENSITIES = {
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}

LEGACY_SIZE_DP = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# --------------------------------------------------------------------------
# Un-matting
# --------------------------------------------------------------------------


def _candidate_alpha(bands, brand):
    """Alpha (0..255) and per-pixel validity for the assumption that the mark
    was drawn in ``brand``.

    A pixel ``C`` observed over a white background with coverage ``a`` and
    true colour ``F`` satisfies ``C = a * F + (1 - a) * 255``, therefore
    ``a = (255 - C) / (255 - F)`` per channel.

    The assumption only holds while ``a <= 1`` in every channel, i.e. while
    the observed pixel is never darker than the brand colour. Testing that
    first is what keeps a near-white charcoal pixel from being misread as a
    desaturated plum pixel: the clamping of the individual channel ratios
    alone would otherwise hide the inconsistency.
    """
    denominator = 255 - brand[0]  # placeholder, replaced per channel below
    alphas = []
    validity = None

    for band, fill in zip(bands, brand):
        denominator = 255 - fill
        numerators = ImageChops.invert(band)
        alphas.append(
            numerators.point(
                lambda v, d=denominator: min(255, (v * 255 + d // 2) // d)
            )
        )
        plausible = numerators.point(lambda v, d=denominator: 255 if v <= d else 0)
        validity = plausible if validity is None else ImageChops.darker(validity, plausible)

    highest = ImageChops.lighter(ImageChops.lighter(alphas[0], alphas[1]), alphas[2])
    lowest = ImageChops.darker(ImageChops.darker(alphas[0], alphas[1]), alphas[2])
    residual = ImageChops.subtract(highest, lowest)

    # An implausible candidate must never win, so score it as maximally
    # inconsistent.
    worst = Image.new("L", bands[0].size, 255)
    residual = Image.composite(residual, worst, validity)
    return alphas[0], residual


def unmatte(source: Image.Image) -> Image.Image:
    """Turn the white-matted artwork into a transparent RGBA master."""
    bands = source.convert("RGB").split()
    size = source.size

    chosen_alpha = None
    chosen_bands = None
    chosen_residual = None

    for brand in BRAND_COLORS:
        alpha, residual = _candidate_alpha(bands, brand)

        if chosen_alpha is None:
            chosen_alpha = alpha
            chosen_bands = [Image.new("L", size, channel) for channel in brand]
            chosen_residual = residual
            continue

        # Keep the candidate whose per-channel alphas agree best.
        better = ImageChops.subtract(chosen_residual, residual)
        mask = better.point(lambda v: 255 if v > 0 else 0)
        chosen_alpha = Image.composite(alpha, chosen_alpha, mask)
        chosen_bands = [
            Image.composite(
                Image.new("L", size, channel), chosen_bands[index], mask
            )
            for index, channel in enumerate(brand)
        ]
        chosen_residual = ImageChops.darker(residual, chosen_residual)

    return Image.merge("RGBA", (*chosen_bands, chosen_alpha))


def _verify_unmatte(source: Image.Image, master: Image.Image):
    """Re-composite the master over white and report the reconstruction error.

    A correct un-matte reproduces the delivered artwork pixel for pixel, so
    only a thin scatter of rounding noise on the antialiased edges may
    differ. A broad error means the palette assumption broke.
    """
    white = Image.new("RGBA", source.size, (*BACKGROUND, 255))
    recomposited = Image.alpha_composite(white, master).convert("RGB")
    original = source.convert("RGB")

    worst = 0
    outliers = 0
    total = source.size[0] * source.size[1]
    for band_o, band_r in zip(original.split(), recomposited.split()):
        difference = ImageChops.difference(band_o, band_r)
        pixels = difference.load()
        width, height = difference.size
        for y in range(height):
            for x in range(width):
                value = pixels[x, y]
                if value > worst:
                    worst = value
                if value > 2:
                    outliers += 1
    return worst, outliers, total


# --------------------------------------------------------------------------
# Master geometry
# --------------------------------------------------------------------------


def crop_to_ink(master: Image.Image) -> Image.Image:
    bbox = master.getchannel("A").getbbox()
    if bbox is None:
        raise SystemExit("no artwork found in the source image")
    return master.crop(bbox)


def ink_radius(master: Image.Image):
    """Return ``(radius, centre)`` of the farthest ink pixel from the centre
    of the artwork's bounding box.

    Only the leftmost and rightmost ink pixel of each row can be farthest
    from the centre, so scanning row extents is enough.
    """
    alpha = master.getchannel("A")
    bbox = alpha.getbbox()
    x0, y0, x1, y1 = bbox
    centre_x = (x0 + x1 - 1) / 2
    centre_y = (y0 + y1 - 1) / 2

    radius = 0.0
    for y in range(y0, y1):
        row = alpha.crop((x0, y, x1, y + 1)).getbbox()
        if row is None:
            continue
        left = x0 + row[0]
        right = x0 + row[2] - 1
        offset = abs(y - centre_y)
        radius = max(
            radius,
            math.hypot(left - centre_x, offset),
            math.hypot(right - centre_x, offset),
        )
    return radius, (centre_x, centre_y)


# --------------------------------------------------------------------------
# Rendering
# --------------------------------------------------------------------------


def scaled_mark(master: Image.Image, master_radius: float, target_radius: float) -> Image.Image:
    """Scale the mark so its farthest ink pixel sits ``target_radius`` away
    from the artwork's centre."""
    if master_radius <= 0:
        raise SystemExit("the mark has no measurable area")
    scale = target_radius / master_radius
    width = max(1, round(master.width * scale))
    height = max(1, round(master.height * scale))
    return master.resize((width, height), Image.LANCZOS)


def centred(canvas: Image.Image, mark: Image.Image) -> Image.Image:
    offset = ((canvas.width - mark.width) // 2, (canvas.height - mark.height) // 2)
    canvas.alpha_composite(mark, offset)
    return canvas


def render_foreground(master, master_radius, canvas_px, density):
    """Transparent adaptive-foreground layer."""
    target = FOREGROUND_INK_RADIUS_DP * density
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    return centred(canvas, scaled_mark(master, master_radius, target))


def render_monochrome(master, master_radius, canvas_px, density):
    """Flat black silhouette used by Android 13+ themed icons."""
    target = FOREGROUND_INK_RADIUS_DP * density
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    mark = scaled_mark(master, master_radius, target)
    silhouette = Image.new("RGBA", mark.size, (0, 0, 0, 0))
    silhouette.putalpha(mark.getchannel("A"))
    return centred(canvas, silhouette)


def render_legacy(master, master_radius, canvas_px, ratio):
    """Square legacy icon: opaque brand-neutral background plus the mark."""
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (*BACKGROUND, 255))
    mark = scaled_mark(master, master_radius, canvas_px * ratio)
    return centred(canvas, mark)


def render_legacy_round(master, master_radius, canvas_px, ratio):
    """Round legacy icon: the same artwork inside an antialiased circle."""
    supersample = 4
    big = canvas_px * supersample

    mask = Image.new("L", (big, big), 0)
    from PIL import ImageDraw

    ImageDraw.Draw(mask).ellipse((0, 0, big - 1, big - 1), fill=255)
    mask = mask.resize((canvas_px, canvas_px), Image.LANCZOS)

    # The RGB channels stay white all the way to the canvas edge and only the
    # alpha channel carries the circle. Keeping the hidden pixels white stops
    # the antialiased rim from compositing as a dark halo over a light
    # launcher background.
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (*BACKGROUND, 255))
    mark = scaled_mark(master, master_radius, canvas_px * ratio)
    canvas.alpha_composite(mark, ((canvas_px - mark.width) // 2, (canvas_px - mark.height) // 2))
    canvas.putalpha(mask)
    return canvas


def render_playstore(master, master_radius, size=PLAYSTORE_SIZE):
    """Play listing icon: fully opaque, no transparency, no rounded corners."""
    canvas = Image.new("RGBA", (size, size), (*BACKGROUND, 255))
    mark = scaled_mark(master, master_radius, size * PLAYSTORE_INK_RADIUS_RATIO)
    canvas.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2))
    return canvas.convert("RGB")


# --------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------


def write(path: str, image: Image.Image) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, "PNG", optimize=True)
    print(f"  wrote {os.path.relpath(path, ROOT)}")


def write_text(path: str, content: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(content)
    print(f"  wrote {os.path.relpath(path, ROOT)}")


def emit(image: Image.Image, relative: str) -> None:
    write(os.path.join(DESIGN_OUT, relative), image)
    write(os.path.join(RES_OUT, relative), image)


def emit_design_only(image: Image.Image, relative: str) -> None:
    """Artifacts that are not Android resources (e.g. the Play listing icon)
    live only in the design hand-off tree."""
    write(os.path.join(DESIGN_OUT, relative), image)


def emit_text(content: str, relative: str) -> None:
    write_text(os.path.join(DESIGN_OUT, relative), content)
    write_text(os.path.join(RES_OUT, relative), content)


# --------------------------------------------------------------------------
# Vector / XML layers
# --------------------------------------------------------------------------

BACKGROUND_VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<!--
    Adaptive icon background layer.

    The delivered artwork (design/output-onlinepngtools.png) is the brand
    mark on a solid white canvas, so the background layer is plain white and
    the foreground carries the mark. Generated by
    design/generate_android_icons.py - do not edit by hand.
-->
<vector
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108"
    xmlns:android="http://schemas.android.com/apk/res/android">
    <path
        android:fillColor="{color}"
        android:pathData="M0,0h108v108h-108z" />
</vector>
"""

ADAPTIVE_ICON = """<?xml version="1.0" encoding="utf-8"?>
<!--
    Adaptive launcher icon.

    foreground - the brand mark on a transparent 108dp canvas, scaled to sit
                 inside the 66dp safe zone so launcher masks cannot clip it;
    background - the flat brand surface behind the mark;
    monochrome - the flat silhouette Android 13+ uses for themed icons.

    Generated by design/generate_android_icons.py - do not edit by hand.
-->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""


def main() -> int:
    if not os.path.exists(SOURCE):
        raise SystemExit(f"missing source artwork: {SOURCE}")

    source = Image.open(SOURCE)
    unmatted = unmatte(source)

    error, outliers, total = _verify_unmatte(source, unmatted)
    if error > 4 or outliers > total * 0.001:
        raise SystemExit(
            f"un-matting failed: worst channel error {error}, "
            f"{outliers} outlier pixels of {total}"
        )

    master = crop_to_ink(unmatted)
    master_radius, _ = ink_radius(master)

    print(f"source      : {os.path.relpath(SOURCE, ROOT)} ({source.size[0]}x{source.size[1]})")
    print(f"mark bbox   : {master.size[0]}x{master.size[1]} px, ink radius {master_radius:.1f} px")
    print(
        f"un-matte    : worst channel error {error}, "
        f"{outliers} outlier pixels of {total} (antialiasing noise)"
    )
    print("adaptive foreground / monochrome / legacy / round:")

    for density_name, density in DENSITIES.items():
        canvas_px = round(ADAPTIVE_CANVAS_DP * density)
        foreground = render_foreground(master, master_radius, canvas_px, density)
        monochrome = render_monochrome(master, master_radius, canvas_px, density)
        emit(foreground, f"mipmap-{density_name}/ic_launcher_foreground.png")
        emit(monochrome, f"mipmap-{density_name}/ic_launcher_monochrome.png")

        legacy_px = round(LEGACY_SIZE_DP[density_name])
        emit(
            render_legacy(master, master_radius, legacy_px, LEGACY_INK_RADIUS_RATIO),
            f"mipmap-{density_name}/ic_launcher.png",
        )
        emit(
            render_legacy_round(master, master_radius, legacy_px, ROUND_INK_RADIUS_RATIO),
            f"mipmap-{density_name}/ic_launcher_round.png",
        )

    emit_design_only(render_playstore(master, master_radius), "playstore-icon.png")

    print("adaptive icon layers:")
    emit_text(
        BACKGROUND_VECTOR.format(color="#%02X%02X%02X" % BACKGROUND),
        "drawable/ic_launcher_background.xml",
    )
    emit_text(ADAPTIVE_ICON, "mipmap-anydpi-v26/ic_launcher.xml")
    emit_text(ADAPTIVE_ICON, "mipmap-anydpi-v26/ic_launcher_round.xml")

    print("done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
