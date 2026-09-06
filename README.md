# Astrology Nova

An Android app for looking at stars and constellations, built by merging two projects that
each do half the job:

- **[Sky Map](https://github.com/sky-map-team/stardroid)** knows where you are pointing. It
  fuses the compass, accelerometer and gyroscope so the map follows the phone, and it has
  fifteen years of work in it on the unglamorous parts — sensor calibration, search, time
  travel, an AR camera mode, widgets, 28 languages.
- **[Stellarium Web Engine](https://github.com/Stellarium/stellarium-web-engine)** (via the
  [Astara](https://github.com/DesolateSea/Astara_stellarium-web-engine) fork) knows what is
  out there. 60,000 stars, deep-sky survey imagery, planets, satellites, comets, a real
  horizon, and constellation figures and artwork from two dozen sky cultures — all offline.

Astrology Nova ships both. The map is what opens; **⋮ → Deep sky** hands the same sky to the
Stellarium engine when you want to browse rather than point.

Everything runs on the phone. No account, no ads, no tracking, no network needed after
install.

---

## Status

| | |
|---|---|
| Android APK | **builds and installs** — `app.astrologynova`, ~74 MB debug |
| Deep-sky view | built into the APK; **not yet run on a physical device** |
| iOS | not started — see below |

### About iPhone

There is no iPhone build here, and an APK will never install on one; they are different
binaries from the same source. Adding iOS means a `WKWebView` shell over the same `web/`
directory, which is real but modest work — and it needs Xcode (~15 GB) plus an Apple ID to
sign with, neither of which was available when this was built. The `web/` directory was laid
out at the top level, rather than inside `android/`, so that shell can share it unchanged.

---

## Build

Needs a JDK 17+ and the Android SDK. Nothing else — the WebAssembly engine is committed
pre-built, so emscripten is only required if you change the engine's C.

```sh
cd android
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # wherever yours is
./gradlew :app:assembleFdroidDebug
```

The APK lands in `android/app/build/outputs/apk/fdroid/debug/`. Install it with
`adb install -r <apk>`.

There are two flavours, inherited from Sky Map: `fdroid` (no Google dependencies — use this)
and `gms` (Play Services location and analytics; needs a `no-checkin.properties` you do not
have).

### Rebuilding the engine

Only if you touch `engine/`:

```sh
git clone https://github.com/emscripten-core/emsdk.git && emsdk/emsdk install latest
emsdk/emsdk activate latest && source emsdk/emsdk_env.sh
python3 -m pip install scons

cd engine && emscons scons -j8 mode=release
cp build/stellarium-web-engine.js build/stellarium-web-engine.wasm ../web/js/
```

`engine/SConstruct` carries the patches that make a 2020-era codebase compile with a current
emscripten; they are listed in [NOTICE.md](NOTICE.md).

### Looking at the sky view without building an APK

```sh
tools/serve-web.sh          # then open http://localhost:8765/
```

Any browser with WebGL 2 will do. This is the fastest way to check a change to `web/`.

`web/` also carries a web-app manifest and the mark as an icon, so "add to home screen" on
the deployed page gives you the Nova icon and opens without browser chrome. That shortcut is
**not the APK** — it has no sensors and no camera, because a web page cannot have Sky Map's
sensor fusion. Regenerate the icons with `tools/export-icons.sh` if the mark changes.

The same directory is what `vercel.json` deploys, so a push to `main` also publishes the sky
view on the web. That deployment is **not a second product** — it is the phone app's WebView
screen, served over HTTP instead of from the APK's assets, which makes it the quickest way to
see a change on a real device without installing anything. The Android app never loads it: it
reads its own bundled copy through `WebViewAssetLoader`, offline.

---

## Layout

```
android/   Sky Map v2, forked and rebranded. Produces the APK.
           └─ app/src/main/kotlin/.../ui/deepsky/   hosts the engine in a WebView
engine/    Stellarium Web Engine's C, patched to build with modern emscripten
web/       what the WebView loads: the page, the built wasm, 22 MB of sky data
design/    the brand sheet — open design/brand-sheet.html in a browser
tools/     helper scripts that are not specific to one module
```

`web/` is copied into the APK's assets by a Gradle task rather than checked in twice; see the
note in `android/app/build.gradle.kts`.

---

## Licence

**AGPL v3.** The engine is AGPL and Sky Map is GPL, and combining them means the app as a
whole is AGPL. [NOTICE.md](NOTICE.md) explains that properly, records every upstream change,
and lists who owns what.

None of Sky Map's reserved brand artwork is here — the launcher icon, the notification glyph,
the onboarding backdrop and the ten sky-marker icons were all redrawn or regenerated for this
app. Not affiliated with Sky Map or Stellarium.
