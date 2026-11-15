# `bg_subscription_band.png`

The plans screen's 320-pt top image band.

## Source

Rasterised from the iOS app's own asset — **not** a redrawn substitute:

```
kornerz/Kornerz/Resources/Assets.xcassets/Auth/subscription_bg.imageset/
    klouders_illustration (1).svg
```

It is the "klouder illustration": a scattering of rounded-square portraits
of community members on a transparent background, which iOS renders at
`kornerz/Kornerz/InAppPurchase/SubscriptionScreen.swift:28-35` with
`.resizable().scaledToFill().clipped()`.

## Why it is a PNG and not a VectorDrawable

The source is an SVG that composites its photo tiles with SVG `<pattern>`
fills and `<mask>` elements:

```xml
<mask id="mask0_4962_840" style="mask-type:alpha" ...>
  <path d="M58.5795 28.5073L27.3403 -2.732C..." fill="#2E2F3E"/>
</mask>
<pattern id="pattern0_4962_840" patternContentUnits="objectBoundingBox" width="1" height="1">
  <use xlink:href="#image0_4962_840" transform="scale(0.000976562)"/>
</pattern>
```

Android's `VectorDrawable` supports neither `<pattern>` nor `<mask>`, so
the artwork cannot be expressed as a vector on Android. It is therefore
rasterised from the identical SVG and shipped as a PNG with its alpha
channel intact, so the portraits sit directly on the screen's aqua
(`#68B6CC`) background exactly as they do on iOS.

The shipped PNG is **750 x 640** — 2x the SVG's 375 x 320-pt logical
size — which is enough for the 320-pt band on a 3x display when the
source's own detail is a 1024-px photo collage.

## Regenerating

If the iOS SVG changes, re-render it and replace this file:

```sh
# Render with a browser engine — WebKit/Blink handle SVG <pattern> and
# <mask>; most SVG rasterisers (cairosvg, resvg) do not.
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless --disable-gpu --no-sandbox \
  --default-background-color=00000000 \
  --screenshot=band.png --window-size=750,640 \
  --hide-scrollbars "file://$PWD/page.html"
```

where `page.html` is an `<img>` of the SVG sized to 750x640 on a
transparent background.

## Attribution

The portrait photographs are original Kornerz artwork and ship with the
product. They are bundled **only** as embedded application resources for
the Kornerz Android app, the same way the iOS app bundles them in its
asset catalog.
