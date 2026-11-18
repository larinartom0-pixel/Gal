package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.util.Locale

/**
 * Controller bridging Jetpack Compose controls (Play/Pause, Seek, Speed 0.25x–3.0x, Loop)
 * to the embedded Chromium HTML5 <video> element inside [WebView].
 *
 * Stores [currentPositionMs] and [durationMs] in volatile fields so 100ms progress polling inside
 * [PlayerTimelineSection] never triggers parent recompositions.
 */
@Stable
class BrowserVideoController {
    @Volatile
    var currentPositionMs: Long = 0L
        internal set

    @Volatile
    var durationMs: Long = 0L
        internal set

    internal var webView: WebView? = null

    fun play() {
        webView?.post {
            webView?.evaluateJavascript("window.vaultPlay && window.vaultPlay();", null)
        }
    }

    fun pause() {
        webView?.post {
            webView?.evaluateJavascript("window.vaultPause && window.vaultPause();", null)
        }
    }

    fun seekTo(positionMs: Long) {
        val safeMs = positionMs.coerceAtLeast(0L)
        currentPositionMs = safeMs
        webView?.post {
            webView?.evaluateJavascript("window.vaultSeekMs && window.vaultSeekMs($safeMs);", null)
        }
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        val formatted = String.format(Locale.US, "%.2f", clamped)
        webView?.post {
            webView?.evaluateJavascript("window.vaultSetSpeed && window.vaultSetSpeed($formatted);", null)
        }
    }

    fun setLoop(loop: Boolean) {
        webView?.post {
            webView?.evaluateJavascript("window.vaultSetLoop && window.vaultSetLoop($loop);", null)
        }
    }

    fun onLifecyclePause() {
        pause()
        webView?.onPause()
    }

    fun onLifecycleResume() {
        webView?.onResume()
    }
}

/**
 * Chromium WebView-based HTML5 video player ("browser player mode") backed by [LocalVideoHttpServer]
 * on 127.0.0.1 for reliable concurrent HTTP 206 byte-range streaming across all MP4/WebM/MKV variants
 * (including non-faststart MP4s with trailing `moov` atoms), plus automatic fallback to Native ExoPlayer
 * if an MP4 uses a codec unsupported by WebView (such as HEVC/H.265).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserVideoPlayer(
    mediaId: Int,
    mediaPath: String,
    speed: Float,
    loopVideo: Boolean,
    initialPositionMs: Long,
    autoStart: Boolean,
    keepScreenOn: Boolean = true,
    controller: BrowserVideoController,
    onBufferingChanged: (Boolean) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onEnded: () -> Unit,
    onUnsupportedCodec: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val latestSpeed = rememberUpdatedState(speed.coerceIn(0.25f, 3.0f))
    val latestLoop = rememberUpdatedState(loopVideo)
    val latestInitialPos = rememberUpdatedState(initialPositionMs.coerceAtLeast(0L))
    val latestAutoStart = rememberUpdatedState(autoStart)
    val latestOnBuffering = rememberUpdatedState(onBufferingChanged)
    val latestOnIsPlaying = rememberUpdatedState(onIsPlayingChanged)
    val latestOnEnded = rememberUpdatedState(onEnded)
    val latestOnUnsupportedCodec = rememberUpdatedState(onUnsupportedCodec)

    val httpServer = remember {
        LocalVideoHttpServer { videoUrl, queryParams ->
            val reqSpeed = queryParams["spd"]?.toFloatOrNull() ?: latestSpeed.value
            val reqLoop = queryParams["lp"]?.toBooleanStrictOrNull() ?: latestLoop.value
            val reqSeekMs = queryParams["seek"]?.toLongOrNull() ?: latestInitialPos.value
            val reqAuto = queryParams["auto"]?.toBooleanStrictOrNull() ?: latestAutoStart.value
            buildBrowserPlayerHtml(
                videoUrl = videoUrl,
                initialSpeed = reqSpeed.coerceIn(0.25f, 3.0f),
                initialLoop = reqLoop,
                initialSeekMs = reqSeekMs.coerceAtLeast(0L),
                autoStart = reqAuto
            )
        }
    }

    val bridge = remember(controller) {
        object {
            @JavascriptInterface
            fun onState(posMs: Long, durMs: Long, isPlaying: Boolean, isBuffering: Boolean) {
                if (posMs >= 0L) controller.currentPositionMs = posMs
                if (durMs > 0L) controller.durationMs = durMs
                mainHandler.post {
                    latestOnIsPlaying.value(isPlaying)
                    latestOnBuffering.value(isBuffering)
                }
            }

            @JavascriptInterface
            fun onMetadata(durMs: Long) {
                if (durMs > 0L) controller.durationMs = durMs
                mainHandler.post {
                    latestOnBuffering.value(false)
                }
            }

            @JavascriptInterface
            fun onEnded() {
                mainHandler.post {
                    latestOnIsPlaying.value(false)
                    latestOnBuffering.value(false)
                    latestOnEnded.value()
                }
            }

            @JavascriptInterface
            fun onError() {
                mainHandler.post {
                    latestOnBuffering.value(false)
                    latestOnIsPlaying.value(false)
                    latestOnUnsupportedCodec.value()
                }
            }
        }
    }

    // Update current media file and load loopback HTTP player URL whenever the media item changes
    LaunchedEffect(mediaId, mediaPath) {
        httpServer.setActiveFile(File(mediaPath))
        controller.currentPositionMs = latestInitialPos.value
        controller.durationMs = 0L
        onBufferingChanged(true)
        controller.webView?.loadUrl(
            httpServer.buildPlayerUrl(
                mediaId = mediaId,
                speed = latestSpeed.value,
                loop = latestLoop.value,
                initialPositionMs = latestInitialPos.value,
                autoStart = latestAutoStart.value
            )
        )
    }

    LaunchedEffect(speed) {
        controller.setSpeed(speed)
    }

    LaunchedEffect(loopVideo) {
        controller.setLoop(loopVideo)
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.webView?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.onPause()
                wv.removeJavascriptInterface("VaultBridge")
                wv.removeAllViews()
                wv.destroy()
            }
            controller.webView = null
            httpServer.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            NonInterceptingWebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(AndroidColor.BLACK)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    allowFileAccess = false
                    allowContentAccess = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }

                addJavascriptInterface(bridge, "VaultBridge")
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()

                httpServer.setActiveFile(File(mediaPath))
                this.keepScreenOn = keepScreenOn
                controller.webView = this
            }
        },
        update = { view ->
            if (controller.webView !== view) {
                controller.webView = view
            }
            if (view.keepScreenOn != keepScreenOn) {
                view.keepScreenOn = keepScreenOn
            }
        },
        modifier = modifier
    )
}

/**
 * WebView subclass that delegates touch events upward to Compose's [detectTapGestures]
 * so single-tap play/pause and double-tap ±10s seek behave identically to the native player.
 */
private class NonInterceptingWebView(context: Context) : WebView(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = false

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false
}

private fun buildBrowserPlayerHtml(
    videoUrl: String,
    initialSpeed: Float,
    initialLoop: Boolean,
    initialSeekMs: Long,
    autoStart: Boolean
): String {
    val speedStr = String.format(Locale.US, "%.2f", initialSpeed)
    val initialSeekSecStr = String.format(Locale.US, "%.3f", initialSeekMs / 1000.0)
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no" />
          <style>
            html, body {
              margin: 0;
              padding: 0;
              width: 100%;
              height: 100%;
              background: #000000;
              overflow: hidden;
            }
            video {
              width: 100%;
              height: 100%;
              object-fit: contain;
              background: #000000;
              display: block;
            }
          </style>
        </head>
        <body>
          <video id="v" src="$videoUrl" playsinline autoplay preload="auto"></video>
          <script>
            (function() {
              var v = document.getElementById('v');
              var targetSpeed = $speedStr;
              var targetLoop = $initialLoop;
              var pendingSeekSec = $initialSeekSecStr;
              var autoStart = $autoStart;
              var appliedInitialSeek = false;
              var userPaused = !autoStart;
              var hadMetadata = false;

              v.loop = targetLoop;
              v.defaultPlaybackRate = targetSpeed;
              v.playbackRate = targetSpeed;
              v.preservesPitch = true;

              function reportState() {
                if (!window.VaultBridge) return;
                var posMs = isFinite(v.currentTime) ? Math.round(v.currentTime * 1000) : 0;
                var durMs = isFinite(v.duration) ? Math.round(v.duration * 1000) : 0;
                var playing = !v.paused && !v.ended;
                var buffering = !v.paused && !v.ended && v.readyState < 3;
                window.VaultBridge.onState(posMs, durMs, playing, buffering);
              }

              v.addEventListener('loadedmetadata', function() {
                hadMetadata = true;
                if (!appliedInitialSeek && pendingSeekSec > 0.05 && isFinite(v.duration)) {
                  appliedInitialSeek = true;
                  v.currentTime = Math.min(pendingSeekSec, v.duration);
                }
                v.defaultPlaybackRate = targetSpeed;
                v.playbackRate = targetSpeed;
                v.preservesPitch = true;
                v.loop = targetLoop;
                if (window.VaultBridge && isFinite(v.duration)) {
                  window.VaultBridge.onMetadata(Math.round(v.duration * 1000));
                }
                if (!userPaused) {
                  v.play().catch(function(){});
                } else {
                  v.pause();
                }
                reportState();
              });

              v.addEventListener('canplay', function() {
                v.playbackRate = targetSpeed;
                if (!userPaused && v.paused && !v.ended) {
                  v.play().catch(function(){});
                }
                reportState();
              });

              v.addEventListener('playing', function() {
                v.playbackRate = targetSpeed;
                reportState();
              });

              v.addEventListener('play', reportState);
              v.addEventListener('pause', reportState);
              v.addEventListener('waiting', reportState);
              v.addEventListener('seeked', reportState);
              v.addEventListener('timeupdate', reportState);

              v.addEventListener('ratechange', function() {
                if (Math.abs(v.playbackRate - targetSpeed) > 0.01) {
                  v.playbackRate = targetSpeed;
                }
              });

              v.addEventListener('ended', function() {
                if (!v.loop && window.VaultBridge) {
                  window.VaultBridge.onEnded();
                }
              });

              v.addEventListener('error', function() {
                if (window.VaultBridge) {
                  window.VaultBridge.onError();
                }
              });

              setInterval(function() {
                if (!v.paused && !v.ended) {
                  reportState();
                }
              }, 120);

              window.vaultPlay = function() {
                userPaused = false;
                v.playbackRate = targetSpeed;
                v.play().catch(function(){});
              };
              window.vaultPause = function() {
                userPaused = true;
                v.pause();
              };
              window.vaultSeekMs = function(ms) {
                if (isFinite(v.duration) && v.duration > 0) {
                  v.currentTime = Math.max(0, Math.min(ms / 1000.0, v.duration));
                } else {
                  pendingSeekSec = ms / 1000.0;
                }
                reportState();
              };
              window.vaultSetSpeed = function(spd) {
                targetSpeed = spd;
                v.defaultPlaybackRate = spd;
                v.playbackRate = spd;
                v.preservesPitch = true;
              };
              window.vaultSetLoop = function(lp) {
                targetLoop = !!lp;
                v.loop = targetLoop;
              };
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}
