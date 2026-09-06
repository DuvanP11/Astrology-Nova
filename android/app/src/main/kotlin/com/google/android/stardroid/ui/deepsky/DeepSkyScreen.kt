/*
 * Copyright (c) 2026 Astrology Nova contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.deepsky

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import com.google.android.stardroid.R
import com.google.android.stardroid.math.LatLong
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The deep-sky view: the Stellarium Web Engine (compiled to WebAssembly, bundled under
 * `assets/web/`) in a WebView, next to — not instead of — the GLES sky map.
 *
 * The two renderers answer different questions. The map is built to be *pointed*: it tracks
 * the phone's sensors, so it answers "what am I looking at right now". The engine is built to
 * be *browsed*: it carries a 60k-star catalogue, deep-sky survey imagery, constellation art
 * from two dozen sky cultures and a real horizon, so it answers "what is out there". Keeping
 * both is the whole reason this app merges two upstreams.
 *
 * ### Why an asset loader rather than `file:///android_asset`
 *
 * A `file://` page is treated as an opaque origin: `fetch` of a sibling file is blocked, and
 * opening that back up (`setAllowFileAccessFromFileURLs`) hands every script in the WebView
 * read access to the app's private storage. [WebViewAssetLoader] instead serves the same
 * files under an `https://` origin that resolves to nothing on the public internet, so the
 * engine's XHRs for the star index behave like ordinary same-origin requests and no file
 * access is granted at all.
 */
private const val ASSET_ORIGIN = "https://appassets.androidplatform.net"
private const val SKY_PAGE = "$ASSET_ORIGIN/assets/web/index.html"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeepSkyScreen(
    location: LatLong?,
    hasCameraPermission: () -> Boolean,
    onRequestCameraPermission: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val assetLoader =
        remember(context) {
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
        }

    // Held so the lifecycle observer and the location effect can reach the same instance the
    // AndroidView factory created.
    val webViewRef = remember { arrayOfNulls<WebView>(1) }

    // Set when the page asked for the camera and the app did not hold the permission yet.
    // The system dialog pauses this activity, so ON_RESUME is where the answer is read back
    // — there is no result callback to hook, the permission launcher lives in the activity.
    val awaitingCameraPermission = remember { AtomicBoolean(false) }

    BackHandler(onBack = onBack)

    Box(
        Modifier
            .fillMaxSize()
            // The page paints its own night sky; matching it here stops a white flash in the
            // frame between the view being attached and the first paint.
            .background(Color(0xFF0B1020)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef[0] = this
                    settings.javaScriptEnabled = true
                    // The engine keeps its own caches in memory; DOM storage is only used for
                    // the page's remembered toggles.
                    settings.domStorageEnabled = true
                    // Deliberately left off: everything the page needs is served by the asset
                    // loader, so neither file nor content access is required.
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    // The page is a fixed-width viewport; letting the WebView re-lay it out
                    // for "desktop" widths would shrink the sky to a corner.
                    settings.useWideViewPort = false
                    settings.loadWithOverviewMode = false
                    setBackgroundColor(0xFF0B1020.toInt())
                    // The camera preview is a muted <video> the page starts itself; without
                    // this the WebView waits for a tap that has already happened.
                    settings.mediaPlaybackRequiresUserGesture = false

                    addJavascriptInterface(NovaHost(this), "NovaHost")

                    webChromeClient =
                        object : WebChromeClient() {
                            // getUserMedia inside the page lands here. The page's origin is
                            // our own asset loader, so the only question is whether the app
                            // itself may use the camera; if it may not, ask for it and let
                            // ON_RESUME below tell the page how that went.
                            override fun onPermissionRequest(request: PermissionRequest) {
                                val wantsCamera =
                                    request.resources.contains(
                                        PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                                    )
                                if (!wantsCamera) {
                                    request.deny()
                                    return
                                }
                                if (hasCameraPermission()) {
                                    request.grant(
                                        arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
                                    )
                                } else {
                                    request.deny()
                                    awaitingCameraPermission.set(true)
                                    onRequestCameraPermission()
                                }
                            }
                        }

                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? =
                                assetLoader.shouldInterceptRequest(request.url)

                            // Nothing in the bundle links out, so any navigation away from the
                            // asset origin is either a bug or something hostile. Refuse it
                            // rather than handing the user a browser they didn't ask for.
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = !request.url.toString().startsWith(ASSET_ORIGIN)
                        }

                    loadUrl(SKY_PAGE)
                }
            },
            update = { web -> web.pushObserver(location) },
            onRelease = { web ->
                webViewRef[0] = null
                web.destroy()
            },
        )

        SmallFloatingActionButton(
            onClick = onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.deep_sky_back),
            )
        }
    }

    // A WebView left running in the background keeps rendering frames and burns battery; the
    // engine's requestAnimationFrame loop makes that expensive rather than merely wasteful.
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                val web = webViewRef[0] ?: return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        web.onPause()
                        web.pauseTimers()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        web.resumeTimers()
                        web.onResume()
                        // Coming back from a long pause, the sky is stale by however long the
                        // app was away.
                        web.evaluateJavascript("window.NovaSky && NovaSky.resumeNow()", null)
                        if (awaitingCameraPermission.getAndSet(false)) {
                            val granted = hasCameraPermission()
                            web.evaluateJavascript(
                                "window.NovaSky && NovaSky.cameraPermissionResult($granted)",
                                null,
                            )
                        }
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Pushes the app's observer position into the page, if the engine has finished booting. The
 * page is written to tolerate this arriving late, early, or repeatedly: the first call after
 * `onSkyReady` is the one that matters, and later calls are how a fresh GPS fix reaches it.
 */
private fun WebView.pushObserver(location: LatLong?) {
    val lat = location?.latitudeDeg ?: return
    val lon = location.longitudeDeg
    evaluateJavascript(
        "window.NovaSky && NovaSky.ready && NovaSky.setObserver($lat, $lon, 0)",
        null,
    )
}

/**
 * The page's two calls back into the app. A JavaScript interface is a hole in the app's
 * process, so it stays as narrow as the job allows: one method to say "the engine is up,
 * send me where we are", and one to hand over a picture the WebView cannot save by itself.
 */
private class NovaHost(private val webView: WebView) {
    @JavascriptInterface
    fun onSkyReady() {
        // Arrives on a WebView-internal thread; touching the view off the main thread throws.
        webView.post {
            webView.evaluateJavascript("NovaSky.setTime(${System.currentTimeMillis()})", null)
        }
    }

    /**
     * Writes a capture into the shared gallery.
     *
     * The page cannot do this itself: a WebView ignores `<a download>`, so a capture taken
     * inside the app would silently go nowhere. It arrives as a `data:` URL because that is
     * what a canvas produces, and it is decoded here rather than in JavaScript so the bytes
     * cross the bridge once.
     *
     * No storage permission is involved. On minSdk 29 and up an app owns what it inserts
     * into MediaStore, and asking for WRITE_EXTERNAL_STORAGE to write a picture the user
     * just took would be asking for far more than the job needs.
     */
    @JavascriptInterface
    fun saveImage(
        dataUrl: String,
        name: String,
    ) {
        // Already off the main thread (a JavascriptInterface call arrives on a WebView
        // thread), so the decode and the write happen here rather than being posted.
        val saved =
            runCatching {
                val comma = dataUrl.indexOf(',')
                require(comma > 0 && dataUrl.startsWith("data:image/")) { "not an image data URL" }
                val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                writeToGallery(webView.context, name, bytes)
            }.getOrDefault(false)

        webView.post {
            webView.evaluateJavascript("window.NovaSky && NovaSky.imageSaved($saved)", null)
        }
    }
}

/** The album captures land in, so they sit together rather than loose in Pictures. */
private const val GALLERY_ALBUM = "Astrology Nova"

private fun writeToGallery(
    context: Context,
    name: String,
    bytes: ByteArray,
): Boolean {
    val resolver = context.contentResolver
    val details =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$GALLERY_ALBUM",
            )
            // Hides the row from the gallery until the bytes are actually there, so a
            // half-written capture is never shown and never left behind if this fails.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

    val uri =
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, details) ?: return false

    return runCatching {
        resolver.openOutputStream(uri).use { stream ->
            requireNotNull(stream) { "no output stream for $uri" }.write(bytes)
        }
        details.clear()
        details.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, details, null, null)
        true
    }.getOrElse {
        // Leaving a pending row behind would be an invisible file the user cannot delete.
        resolver.delete(uri, null, null)
        false
    }
}
