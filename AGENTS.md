# AGENTS.md

Context for AI coding assistants working in this repository. Per-module notes live in
[`android/AGENTS.md`](android/AGENTS.md) — read that one before touching Kotlin.

## What this is

Astrology Nova merges two upstreams into one Android app: Sky Map (native, sensor-driven) in
`android/`, and the Stellarium Web Engine (C → WebAssembly) in `engine/`, surfaced through a
WebView screen. `web/` is the bundle the WebView loads and the build stages into the APK's
assets.

The merge is deliberately *seam-visible* rather than blended: the two renderers stay separate
screens because they answer different questions (point vs. browse), and keeping the Kotlin
package namespace as upstream's means their fixes still merge.

## Rules that are easy to get wrong here

- **Do not rename the `com.google.android.stardroid` package.** Only the application id
  changed. Renaming 329 files would end the ability to merge upstream and buys nothing.
- **Do not copy anything back from Sky Map's `[arr]` asset list.** Its launcher icon,
  notification glyph, onboarding backdrop and marker icons are All Rights Reserved and were
  replaced here on purpose. `android/tools/check_asset_licenses.py` will not catch this — the
  paths still match — so it is on you.
- **Do not narrow a GPL header to bare GPLv3.** "or later" is what makes combining with the
  AGPL engine legal. See [NOTICE.md](NOTICE.md).
- **`web/js/*.wasm` is committed build output.** If you change `engine/`, rebuild it and
  commit the result, or the APK silently keeps the old engine.
- **`vercel.json` publishes `web/`, not the repo.** A push to `main` redeploys the sky view
  as a static site. It shares the directory the APK bundles, so a change to `web/` ships to
  both at once — and breaking the page breaks the app's deep-sky screen too.
- **`android/docs/` describes Sky Map, not this app.** It was inherited unchanged. Do not
  treat it as a specification for anything here.

## Building

See [README.md](README.md). Short version: `cd android && ./gradlew :app:assembleFdroidDebug`.
Always name the flavour (`assembleFdroidDebug`, not `assembleDebug`).

Before committing Kotlin: `cd android && ./gradlew ktlintCheck`.

## Strings

New user-facing strings go in `android/app/src/main/res/values/strings.xml` in **US English**
with a `translation_description`, and are escaped for Android (`'` becomes `\'`). The 28
locale directories are upstream's; a new string simply falls back to English until translated.

The deep-sky page (`web/index.html`) carries its own two-language string table — its whole
vocabulary is eight toggles and five row labels, and wiring it into the Android translation
pipeline would cost more than it returns.
