#!/bin/sh
# Re-export web/'s icons from the mark, so the home-screen shortcut and the APK's
# launcher icon never drift apart.
#
# The source of truth is android/app/src/main/res/drawable/ic_launcher_*.xml. Those are
# Android vector drawables, which nothing outside Android renders, so the same geometry is
# kept as web/icon.svg and rasterised from there. If you change the drawables, change
# icon.svg to match and re-run this.
#
# Rasterises with macOS Quick Look (qlmanage) rather than a converter this machine would
# have to install; on Linux use `rsvg-convert -w 1024 icon.svg > icon.png` instead.
set -e
cd "$(dirname "$0")/../web"

command -v qlmanage >/dev/null 2>&1 || { echo "qlmanage not found (macOS only)"; exit 1; }

tmp=$(mktemp -d)
qlmanage -t -s 1024 -o "$tmp" icon.svg >/dev/null 2>&1
python3 - "$tmp/icon.svg.png" <<'PY'
import sys
from PIL import Image
src = Image.open(sys.argv[1]).convert("RGB")
for size, name in ((192, "icon-192.png"), (512, "icon-512.png"), (180, "apple-touch-icon.png")):
    src.resize((size, size), Image.LANCZOS).save(name, optimize=True)
    print(f"  wrote {name}")
PY
rm -rf "$tmp"
