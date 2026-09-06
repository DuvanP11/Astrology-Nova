# Room, Hilt, Compose, Coil, kotlinx.coroutines, and the Firebase SDKs (gms flavor) all ship
# their own consumer ProGuard rules, so no project-specific keep rules are needed for them.
# This file is intentionally empty pending a case that AndroidX's bundled rules don't cover.

# The deep-sky view's bridge into the page. R8 has no way to see that the WebView
# calls these reflectively from JavaScript, so without this the release build
# strips onSkyReady and the engine never learns where the observer is.
-keepclassmembers class com.google.android.stardroid.ui.deepsky.** {
    @android.webkit.JavascriptInterface <methods>;
}
