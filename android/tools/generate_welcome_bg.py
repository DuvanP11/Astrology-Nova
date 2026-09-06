#!/usr/bin/env python3
# Copyright (c) 2026 Astrology Nova contributors.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
"""Generate the warm-welcome backdrop, `res/drawable-nodpi/welcome_sky_bg.webp`.

The upstream backdrop is Sky Map brand artwork and All Rights Reserved, so a fork
cannot ship it. This draws a replacement rather than hand-editing a bitmap: the
image is deterministic (fixed seed), so re-running reproduces the shipped file, and
changing the look is a diff instead of a binary.

It deliberately reproduces the *kind* of image the original was — a sky the app has
drawn, with constellation lines, catalogue labels and a deep-sky object — because
that is what the onboarding text is introducing. An abstract nebula would look
prettier and promise the wrong thing.

The geometry is Orion as it actually appears from northern mid-latitudes looking
south: Betelgeuse on the eastern (left) shoulder, Rigel at the western foot, the
belt running down-left to up-right, M42 hanging below it. Getting that backwards in
an astronomy app's own onboarding would be a poor first impression.

    python3 tools/generate_welcome_bg.py

Requires Pillow (`python3 -m pip install Pillow`).
"""

from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1080, 1834
SEED = 20260905

# Nova's palette.
GROUND_TOP = (8, 11, 26)
GROUND_BOTTOM = (20, 28, 60)
STARLIGHT = (230, 236, 255)
CYAN = (90, 242, 255)
VIOLET = (155, 123, 255)
DIM = (154, 163, 199)
LINE = (124, 136, 190)

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "android/app/src/main/res/drawable-nodpi/welcome_sky_bg.webp"
FONT_REGULAR = ROOT / "web/fonts/Roboto-Regular.ttf"

# Orion, in fractions of the canvas. Names are the stars' own; the app's catalogue
# uses the same ones.
STARS = {
    "Betelgeuse":  (0.235, 0.250, 3.2),
    "Bellatrix":   (0.660, 0.222, 2.6),
    "Alnitak":     (0.360, 0.474, 2.6),
    "Alnilam":     (0.452, 0.462, 2.8),
    "Mintaka":     (0.545, 0.448, 2.5),
    "Saiph":       (0.330, 0.726, 2.5),
    "Rigel":       (0.720, 0.712, 3.3),
    "Meissa":      (0.455, 0.118, 1.8),
}

# The figure: shoulders, sides, belt, legs, head.
FIGURE = [
    ("Betelgeuse", "Bellatrix"),
    ("Betelgeuse", "Alnitak"),
    ("Bellatrix", "Mintaka"),
    ("Alnitak", "Alnilam"),
    ("Alnilam", "Mintaka"),
    ("Alnitak", "Saiph"),
    ("Mintaka", "Rigel"),
    ("Saiph", "Rigel"),
    ("Betelgeuse", "Meissa"),
    ("Bellatrix", "Meissa"),
]

# The sword, hanging below the belt, with the Orion Nebula in it.
SWORD = [(0.430, 0.560), (0.418, 0.618), (0.408, 0.668)]
M42 = (0.418, 0.618)

# Label anchor: "l" places the text to the left of the star, "r" to the right.
LABELS = {
    "Betelgeuse": "r", "Bellatrix": "l", "Rigel": "l",
    "Saiph": "r", "Alnilam": "r", "Meissa": "r",
}


def px(p: tuple[float, float]) -> tuple[float, float]:
    return (p[0] * W, p[1] * H)


def vertical_gradient() -> Image.Image:
    """The base sky: darkest overhead, lifting toward the horizon at the bottom."""
    img = Image.new("RGB", (1, H))
    pixels = img.load()
    for y in range(H):
        t = y / (H - 1)
        t = t * t * (3 - 2 * t)
        pixels[0, y] = tuple(
            round(a + (b - a) * t) for a, b in zip(GROUND_TOP, GROUND_BOTTOM)
        )
    return img.resize((W, H), Image.BILINEAR)


def milky_way(img: Image.Image) -> None:
    """The faint band Orion sits beside, running down the eastern side."""
    band = Image.new("L", (W, H), 0)
    draw = ImageDraw.Draw(band)
    for i in range(200):
        t = i / 199
        x = W * 1.02 - t * W * 0.55
        y = -100 + t * (H + 200)
        half = 130 + math.sin(t * 3.1) * 45
        draw.ellipse([x - half, y - 90, x + half, y + 90], fill=26)
    band = band.filter(ImageFilter.GaussianBlur(80))
    layer = Image.new("RGB", (W, H), (150, 162, 215))
    layer.putalpha(band)
    img.alpha_composite(layer)


def field(img: Image.Image, rng: random.Random) -> None:
    """The background catalogue: thousands of faint stars, drawn as Sky Map draws
    them — small squares, not discs, which is what a point sampled onto a pixel
    grid honestly looks like."""
    draw = ImageDraw.Draw(img, "RGBA")
    for _ in range(1500):
        x, y = rng.uniform(0, W), rng.uniform(0, H)
        brightness = rng.random() ** 2.6
        size = 2 if brightness < 0.55 else (3 if brightness < 0.88 else 4)
        alpha = round(70 + 150 * brightness)
        tint = rng.random()
        colour = VIOLET if tint < 0.07 else (CYAN if tint < 0.14 else DIM)
        draw.rectangle([x, y, x + size - 1, y + size - 1], fill=colour + (alpha,))


def glow(img: Image.Image, centre: tuple[float, float], radius: float,
         colour: tuple[int, int, int], peak: int) -> None:
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    cx, cy = centre
    steps = 14
    for i in range(steps, 0, -1):
        r = radius * i / steps
        draw.ellipse([cx - r, cy - r, cx + r, cy + r],
                     fill=colour + (round(peak * (1 - i / steps) ** 2),))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius * 0.2)))


def constellation(img: Image.Image) -> None:
    draw = ImageDraw.Draw(img, "RGBA")

    for a, b in FIGURE:
        draw.line([px(STARS[a][:2]), px(STARS[b][:2])], fill=LINE + (130,), width=2)

    # The sword hangs off the belt's midpoint, dimmer than the figure itself.
    draw.line([px(STARS["Alnilam"][:2])] + [px(p) for p in SWORD],
              fill=LINE + (85,), width=2)

    # M42: a fuzzy patch, the one thing here that is not a point source.
    glow(img, px(M42), 46, (200, 150, 255), 40)
    glow(img, px(M42), 22, CYAN, 55)

    for name, (fx, fy, size) in STARS.items():
        x, y = px((fx, fy))
        glow(img, (x, y), size * 9, STARLIGHT, 30)
        half = size * 2.2
        draw.rectangle([x - half, y - half, x + half, y + half],
                       fill=STARLIGHT + (255,))


def labels(img: Image.Image) -> None:
    draw = ImageDraw.Draw(img, "RGBA")
    star_font = ImageFont.truetype(str(FONT_REGULAR), 34)
    dso_font = ImageFont.truetype(str(FONT_REGULAR), 29)
    name_font = ImageFont.truetype(str(FONT_REGULAR), 46)

    for name, side in LABELS.items():
        x, y = px(STARS[name][:2])
        width = draw.textlength(name, font=star_font)
        tx = x + 22 if side == "r" else x - 22 - width
        draw.text((tx, y - 20), name, font=star_font, fill=STARLIGHT + (232,))

    mx, my = px(M42)
    draw.text((mx + 54, my - 16), "Orion Nebula", font=dso_font, fill=CYAN + (225,))

    draw.text((W * 0.60, H * 0.352), "Orion", font=name_font, fill=VIOLET + (235,))


def main() -> None:
    rng = random.Random(SEED)
    img = vertical_gradient().convert("RGBA")
    milky_way(img)
    field(img, rng)
    constellation(img)
    labels(img)
    img.convert("RGB").save(OUT, "WEBP", quality=90, method=6)
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
