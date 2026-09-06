#!/usr/bin/env python3
# Copyright (c) 2026 Astrology Nova contributors.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
"""Generate the sky-marker icon set, `app/src/main/assets/catalog/icons/*.webp`.

Upstream's marker artwork is Sky Map brand identity and All Rights Reserved, so a
fork has to draw its own. These are deliberately *not* a redrawing of upstream's
glyphs: each one is the conventional cartographic symbol for its object class, the
ones printed star atlases and Stellarium have used for decades — a dashed circle for
an open cluster, a crossed circle for a globular, a ticked circle for a planetary
nebula, an open square for a diffuse nebula, an ellipse for a galaxy. Convention is
both the safe choice here and the better one: an observer who has used any other
atlas can already read them.

Drawing conventions inherited from the layer they feed, which are functional rather
than stylistic and so are kept:

  * a 24-unit grid, stroke 1.4, round caps and joins;
  * strokes in white, because the renderer multiply-tints the texture at runtime to
    whatever colour the layer is drawn in (and red-shifts it in night mode);
  * a black halo baked underneath at 35% opacity, stroke + 2.2 wide, so the glyph
    stays legible against a bright Milky Way or a landscape;
  * 4x density bitmaps: 80x80 for the 20dp deep-sky markers, 128x128 for the 32dp
    meteor radiants.

Deterministic: re-running reproduces the shipped files.

    python3 tools/generate_marker_icons.py

Requires Pillow with webp support (`python3 -m pip install Pillow`).
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

GRID = 24.0
STROKE = 1.4
HALO_EXTRA = 2.2
HALO_OPACITY = 0.35
SUPERSAMPLE = 8

OUT_DIR = Path(__file__).resolve().parents[1] / "app/src/main/assets/catalog/icons"

C = GRID / 2  # centre of the grid


# ---------------------------------------------------------------------------
# Geometry primitives, all in grid units:
#   ("dot", cx, cy, r)                    filled circle
#   ("ring", cx, cy, r, width_mul)        stroked circle
#   ("arc", cx, cy, r, start, end, mul)   stroked arc, degrees, 0 = east, CCW
#   ("line", [(x, y), ...], closed, mul)  stroked polyline
# ---------------------------------------------------------------------------


def _dashed_ring(r: float, dashes: int, duty: float = 0.62, mul: float = 1.0):
    """A ring broken into `dashes` evenly spaced arcs."""
    step = 360.0 / dashes
    return [
        ("arc", C, C, r, i * step, i * step + step * duty, mul)
        for i in range(dashes)
    ]


def _radial_ticks(r_inner: float, r_outer: float, count: int, phase: float = 0.0,
                  mul: float = 1.0):
    out = []
    for i in range(count):
        a = math.radians(phase + i * 360.0 / count)
        out.append(("line",
                    [(C + r_inner * math.cos(a), C - r_inner * math.sin(a)),
                     (C + r_outer * math.cos(a), C - r_outer * math.sin(a))],
                    False, mul))
    return out


def _arrow(angle_deg: float, r_start: float, r_end: float, head: float = 2.0):
    """A radial shaft with an open arrowhead — meteors stream *away* from a radiant,
    and the direction is the whole point of the symbol."""
    a = math.radians(angle_deg)
    ca, sa = math.cos(a), -math.sin(a)
    tip = (C + r_end * ca, C + r_end * sa)
    shaft = ("line", [(C + r_start * ca, C + r_start * sa), tip], False, 1.0)
    barbs = []
    for spread in (150, -150):
        b = math.radians(angle_deg + spread)
        barbs.append(("line",
                      [tip, (tip[0] + head * math.cos(b), tip[1] - head * math.sin(b))],
                      False, 1.0))
    return [shaft] + barbs


def galaxy():
    """A tilted lens with a bright nucleus — the atlas symbol for a galaxy."""
    shapes = []
    rx, ry, tilt = 8.6, 4.2, -28.0
    pts = []
    for i in range(49):
        a = math.radians(i * 360 / 48)
        x, y = rx * math.cos(a), ry * math.sin(a)
        t = math.radians(tilt)
        pts.append((C + x * math.cos(t) - y * math.sin(t),
                    C + x * math.sin(t) + y * math.cos(t)))
    shapes.append(("line", pts, True, 1.0))
    shapes.append(("dot", C, C, 1.9))
    return shapes


def diffuse_nebula():
    """An open square with a star inside: the atlas symbol for bright nebulosity."""
    h = 7.4
    shapes = [("line",
               [(C - h, C - h), (C + h, C - h), (C + h, C + h), (C - h, C + h)],
               True, 1.0)]
    shapes.append(("dot", C, C, 1.5))
    return shapes


def open_cluster():
    """A dashed circle — loose, resolvable, no defined edge."""
    return _dashed_ring(7.8, 8) + [
        ("dot", C - 2.4, C - 1.6, 0.85),
        ("dot", C + 2.2, C - 2.4, 0.85),
        ("dot", C + 1.4, C + 2.6, 0.85),
        ("dot", C - 2.0, C + 2.2, 0.85),
    ]


def globular_cluster():
    """A circle crossed by two lines — dense, symmetric, unresolvable."""
    r = 7.8
    return [
        ("ring", C, C, r, 1.0),
        ("line", [(C - r, C), (C + r, C)], False, 1.0),
        ("line", [(C, C - r), (C, C + r)], False, 1.0),
    ]


def planetary_nebula():
    """A small disc with four radial ticks — a dying star's shed shell."""
    return [("ring", C, C, 4.6, 1.0), ("dot", C, C, 1.2)] + \
        _radial_ticks(6.0, 8.6, 4, phase=45.0)


def supernova_remnant():
    """A broken, expanding shell: two nested ring fragments, offset in phase."""
    return (
        [("arc", C, C, 8.2, s, s + 52, 1.0) for s in (10, 105, 200, 295)]
        + [("arc", C, C, 4.6, s, s + 48, 1.0) for s in (60, 160, 250, 340)]
    )


def asterism():
    """Three stars joined into a closed figure — the Summer Triangle, in miniature.
    A closed shape, unlike a constellation line, which is what an asterism is: a
    pattern people agree on, not a bounded region of sky."""
    pts = [(C, C - 7.6), (C + 6.9, C + 5.2), (C - 6.9, C + 5.2)]
    return [("line", pts, True, 1.0)] + [("dot", x, y, 1.5) for x, y in pts]


def other():
    """Unclassified: a plain hexagon, deliberately meaning nothing in particular."""
    pts = [
        (C + 7.6 * math.cos(math.radians(90 + i * 60)),
         C - 7.6 * math.sin(math.radians(90 + i * 60)))
        for i in range(6)
    ]
    return [("line", pts, True, 1.0)]


def meteor_radiant():
    """Arrows streaming out of a point: where a shower's meteors appear to come from."""
    shapes = [("ring", C, C, 2.1, 1.0)]
    for i in range(6):
        shapes += _arrow(i * 60, 3.6, 9.4)
    return shapes


def meteor_radiant_peak():
    """The same radiant on the night it peaks: more meteors, and a ring saying
    'tonight'."""
    shapes = [("dot", C, C, 2.0)]
    for i in range(8):
        shapes += _arrow(i * 45, 3.4, 9.0, head=1.8)
    shapes += _dashed_ring(10.9, 12, duty=0.5)
    return shapes


ICONS = {
    "galaxy": (galaxy, 80),
    "diffuse_nebula": (diffuse_nebula, 80),
    "open_cluster": (open_cluster, 80),
    "globular_cluster": (globular_cluster, 80),
    "planetary_nebula": (planetary_nebula, 80),
    "supernova_remnant": (supernova_remnant, 80),
    "asterism": (asterism, 80),
    "other": (other, 80),
    "meteor_radiant": (meteor_radiant, 128),
    "meteor_radiant_peak": (meteor_radiant_peak, 128),
}


def _paint(draw: ImageDraw.ImageDraw, shapes, scale: float, width_add: float,
           colour: tuple[int, int, int, int]) -> None:
    """Render `shapes` once. `width_add` is 0 for the glyph and HALO_EXTRA for the
    underlay, which is what makes the halo a uniform outset of the glyph rather than
    a blur of it."""
    def s(v: float) -> float:
        return v * scale

    for shape in shapes:
        kind = shape[0]
        if kind == "dot":
            _, cx, cy, r = shape
            rr = s(r) + s(width_add) / 2
            draw.ellipse([s(cx) - rr, s(cy) - rr, s(cx) + rr, s(cy) + rr], fill=colour)
        elif kind == "ring":
            _, cx, cy, r, mul = shape
            w = s(STROKE * mul + width_add)
            draw.ellipse([s(cx - r), s(cy - r), s(cx + r), s(cy + r)],
                         outline=colour, width=max(1, round(w)))
        elif kind == "arc":
            _, cx, cy, r, start, end, mul = shape
            w = s(STROKE * mul + width_add)
            # Pillow measures arcs clockwise from east; the shapes above are written
            # counter-clockwise, which is the convention every other angle here uses.
            draw.arc([s(cx - r), s(cy - r), s(cx + r), s(cy + r)],
                     -end, -start, fill=colour, width=max(1, round(w)))
        elif kind == "line":
            _, pts, closed, mul = shape
            w = max(1, round(s(STROKE * mul + width_add)))
            path = [(s(x), s(y)) for x, y in pts]
            if closed:
                path = path + [path[0]]
            draw.line(path, fill=colour, width=w, joint="curve")
            # Pillow has no round cap; a dot at each vertex is the same thing.
            r = w / 2
            for x, y in path:
                draw.ellipse([x - r, y - r, x + r, y + r], fill=colour)


def render(shapes, size: int) -> Image.Image:
    big = size * SUPERSAMPLE
    scale = big / GRID
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))

    halo = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    _paint(ImageDraw.Draw(halo), shapes, scale, HALO_EXTRA,
           (0, 0, 0, round(255 * HALO_OPACITY)))
    img.alpha_composite(halo)

    _paint(ImageDraw.Draw(img), shapes, scale, 0.0, (255, 255, 255, 255))
    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, (builder, size) in ICONS.items():
        out = OUT_DIR / f"{name}.webp"
        render(builder(), size).save(out, "WEBP", lossless=True, method=6)
        print(f"  {name:<22} {size}x{size}  {out.stat().st_size // 1024 or 1} KB")


if __name__ == "__main__":
    main()
