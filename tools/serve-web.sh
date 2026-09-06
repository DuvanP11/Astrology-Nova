#!/bin/sh
# Serve web/ for a browser, which is the quickest way to look at a change to the
# deep-sky page without building an APK for it.
#
# It has to be a server, not a file:// URL: the engine fetches the star index and
# the wasm module over XHR, and a file:// page is an opaque origin that blocks
# both. This mirrors what WebViewAssetLoader does inside the app.
set -e
cd "$(dirname "$0")/../web"
PORT="${1:-8765}"
echo "Astrology Nova sky view: http://localhost:$PORT/"
exec python3 -m http.server "$PORT"
