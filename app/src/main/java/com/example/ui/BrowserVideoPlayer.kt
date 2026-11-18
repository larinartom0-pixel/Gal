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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLConnection
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

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
 * Chromium WebView-based HTML5 video player ("browser player mode") with full HTTP 206 byte-range
 * local streaming, hardware layer acceleration, instant seeking, and 0.25x–3.0x speed support.
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
    modifier: Modifier = Modifier
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val activeFileRef = remember { AtomicReference<File?>(null) }

    val latestSpeed = rememberUpdatedState(speed.coerceIn(0.25f, 3.0f))
    val latestLoop = rememberUpdatedState(loopVideo)
    val latestInitialPos = rememberUpdatedState(initialPositionMs.coerceAtLeast(0L))
    val latestAutoStart = rememberUpdatedState(autoStart)
    val latestOnBuffering = rememberUpdatedState(onBufferingChanged)
    val latestOnIsPlaying = rememberUpdatedState(onIsPlayingChanged)
    val latestOnEnded = rememberUpdatedState(onEnded)

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
                }
            }
        }
    }

    // Update current media file and load fresh virtual URL whenever the media item changes
    LaunchedEffect(mediaId, mediaPath) {
        activeFileRef.set(File(mediaPath))
        controller.currentPositionMs = latestInitialPos.value
        controller.durationMs = 0L
        onBufferingChanged(true)
        controller.webView?.loadUrl(
            buildPlayerPageUrl(
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
                }

                addJavascriptInterface(bridge, "VaultBridge")
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url ?: return null
                        if (url.host != VIRTUAL_HOST) return null

                        return when (url.path) {
                            "/player.html" -> {
                                val reqSpeed = url.getQueryParameter("spd")?.toFloatOrNull() ?: latestSpeed.value
                                val reqLoop = url.getQueryParameter("lp")?.toBooleanStrictOrNull() ?: latestLoop.value
                                val reqSeekMs = url.getQueryParameter("seek")?.toLongOrNull() ?: latestInitialPos.value
                                val reqAuto = url.getQueryParameter("auto")?.toBooleanStrictOrNull() ?: latestAutoStart.value
                                val reqToken = url.getQueryParameter("v") ?: mediaId.toString()
                                val html = buildBrowserPlayerHtml(
                                    videoUrl = "https://$VIRTUAL_HOST/video?v=$reqToken",
                                    initialSpeed = reqSpeed.coerceIn(0.25f, 3.0f),
                                    initialLoop = reqLoop,
                                    initialSeekMs = reqSeekMs.coerceAtLeast(0L),
                                    autoStart = reqAuto
                                )
                                WebResourceResponse(
                                    "text/html",
                                    "UTF-8",
                                    200,
                                    "OK",
                                    mapOf("Cache-Control" to "no-store"),
                                    ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
                                )
                            }
                            "/video" -> {
                                val file = activeFileRef.get()
                                if (file == null || !file.exists() || !file.isFile) {
                                    WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        404,
                                        "Not Found",
                                        emptyMap(),
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                } else {
                                    serveVideoWithRanges(file, request.requestHeaders["Range"] ?: request.requestHeaders["range"])
                                }
                            }
                            else -> null
                        }
                    }
                }

                activeFileRef.set(File(mediaPath))
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

/**
 * Bounded [InputStream] backed by [RandomAccessFile] for zero-copy HTTP 206 byte-range streaming.
 */
private class RangeFileInputStream(
    file: File,
    startOffset: Long,
    length: Long
) : InputStream() {
    private val raf = RandomAccessFile(file, "r")
    private var remaining: Long = length.coerceAtLeast(0L)

    init {
        raf.seek(startOffset.coerceAtLeast(0L))
    }

    override fun read(): Int {
        if (remaining <= 0L) return -1
        val b = raf.read()
        if (b != -1) {
            remaining--
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0L) return -1
        val toRead = min(len.toLong(), remaining).toInt()
        if (toRead <= 0) return 0
        val count = raf.read(b, off, toRead)
        if (count > 0) {
            remaining -= count.toLong()
        }
        return count
    }

    override fun available(): Int {
        return remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun close() {
        try {
            raf.close()
        } catch (_: Exception) {
        }
    }
}

private const val VIRTUAL_HOST = "vault.local"

private fun buildPlayerPageUrl(
    mediaId: Int,
    speed: Float,
    loop: Boolean,
    initialPositionMs: Long,
    autoStart: Boolean
): String {
    val token = "${mediaId}_${System.nanoTime()}"
    val spd = String.format(Locale.US, "%.2f", speed.coerceIn(0.25f, 3.0f))
    return "https://$VIRTUAL_HOST/player.html?v=$token&spd=$spd&lp=$loop&seek=${initialPositionMs.coerceAtLeast(0L)}&auto=$autoStart"
}

private fun serveVideoWithRanges(file: File, rangeHeader: String?): WebResourceResponse {
    val fileLength = file.length()
    val mimeType = guessVideoMimeType(file)

    if (fileLength <= 0L) {
        return WebResourceResponse(
            mimeType,
            null,
            200,
            "OK",
            mapOf("Content-Length" to "0", "Accept-Ranges" to "bytes", "Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    var start = 0L
    var end = fileLength - 1L
    var isPartial = false

    if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
        val spec = rangeHeader.substringAfter("=").substringBefore(",").trim()
        val parts = spec.split("-")
        if (parts.size == 2) {
            val startStr = parts[0].trim()
            val endStr = parts[1].trim()
            if (startStr.isNotEmpty()) {
                start = startStr.toLongOrNull() ?: 0L
                end = if (endStr.isNotEmpty()) (endStr.toLongOrNull() ?: (fileLength - 1L)) else (fileLength - 1L)
                isPartial = true
            } else if (endStr.isNotEmpty()) {
                val suffixLength = endStr.toLongOrNull() ?: 0L
                if (suffixLength > 0L) {
                    start = (fileLength - suffixLength).coerceAtLeast(0L)
                    end = fileLength - 1L
                    isPartial = true
                }
            }
        }
    }

    if (start < 0L || start >= fileLength || end < start) {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            416,
            "Range Not Satisfiable",
            mapOf("Content-Range" to "bytes */$fileLength"),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    end = end.coerceAtMost(fileLength - 1L)
    val contentLength = end - start + 1L
    val headers = HashMap<String, String>(6).apply {
        put("Accept-Ranges", "bytes")
        put("Content-Length", contentLength.toString())
        put("Cache-Control", "no-store")
        put("Access-Control-Allow-Origin", "https://$VIRTUAL_HOST")
        if (isPartial) {
            put("Content-Range", "bytes $start-$end/$fileLength")
        }
    }

    return WebResourceResponse(
        mimeType,
        null,
        if (isPartial) 206 else 200,
        if (isPartial) "Partial Content" else "OK",
        headers,
        RangeFileInputStream(file, start, contentLength)
    )
}

private fun guessVideoMimeType(file: File): String {
    return when (file.extension.lowercase(Locale.US)) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "avi" -> "video/x-msvideo"
        "ts", "m2ts" -> "video/mp2t"
        else -> URLConnection.guessContentTypeFromName(file.name) ?: "video/mp4"
    }
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
          <video id="v" src="$videoUrl" playsinline preload="auto"></video>
          <script>
            (function() {
              var v = document.getElementById('v');
              var targetSpeed = $speedStr;
              var targetLoop = $initialLoop;
              var pendingSeekSec = $initialSeekSecStr;
              var autoStart = $autoStart;
              var appliedInitialSeek = false;

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
                if (autoStart) {
                  v.play().catch(function(){});
                }
                reportState();
              });

              v.addEventListener('canplay', function() {
                v.playbackRate = targetSpeed;
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
                v.playbackRate = targetSpeed;
                v.play().catch(function(){});
              };
              window.vaultPause = function() {
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
