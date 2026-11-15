package com.example.ui

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import kotlin.math.max

/**
 * Custom [DefaultRenderersFactory] tuned for smooth frame pacing and zero accidental frame drops
 * on 120Hz AMOLED HyperOS/MIUI devices (e.g. Xiaomi Redmi Note 15) across 0.25x–3.0x playback speeds.
 */
@OptIn(UnstableApi::class)
class SmoothRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_OFF)
        setEnableDecoderFallback(true)
        setAllowedVideoJoiningTimeMs(5_000L)
        forceEnableMediaCodecAsynchronousQueueing()
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        val videoRenderer = SmoothMediaCodecVideoRenderer(
            context = context,
            codecAdapterFactory = codecAdapterFactory,
            mediaCodecSelector = mediaCodecSelector,
            allowedJoiningTimeMs = allowedVideoJoiningTimeMs,
            enableDecoderFallback = enableDecoderFallback,
            eventHandler = eventHandler,
            eventListener = eventListener,
            maxDroppedFramesToNotify = MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
        )
        out.add(videoRenderer)
    }
}

/**
 * [MediaCodecVideoRenderer] subclass that prevents aggressive micro-jitter frame drops and
 * multi-second keyframe skips caused by audio clock drift (SonicAudioProcessor at 1.25x–3.0x)
 * and HyperOS SoC power-saving clock throttling.
 */
@OptIn(UnstableApi::class)
private class SmoothMediaCodecVideoRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    allowedJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
    maxDroppedFramesToNotify: Int
) : MediaCodecVideoRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    allowedJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    maxDroppedFramesToNotify
) {

    override fun shouldDropOutputBuffer(
        earlyUs: Long,
        elapsedRealtimeUs: Long,
        isLastBuffer: Boolean
    ): Boolean {
        // Default ExoPlayer drops at -30ms (30_000us). On 120Hz HyperOS devices, B-frame decode
        // spikes and SonicAudioProcessor clock jitter regularly cause 35–100ms variance.
        // Raise tolerance to 200ms so transient jitter never drops visible frames.
        return !isLastBuffer && earlyUs < -200_000L
    }

    override fun shouldDropBuffersToKeyframe(
        earlyUs: Long,
        elapsedRealtimeUs: Long,
        isLastBuffer: Boolean
    ): Boolean {
        // Default -500ms causes jarring multi-second skips to the next keyframe.
        // Only jump to a keyframe if the decoder is genuinely >2.5 seconds behind.
        return !isLastBuffer && earlyUs < -2_500_000L
    }

    override fun getCodecOperatingRateV23(
        targetPlaybackSpeed: Float,
        format: Format,
        streamFormats: Array<Format>
    ): Float {
        val superRate = super.getCodecOperatingRateV23(targetPlaybackSpeed, format, streamFormats)
        val baseFps = if (format.frameRate > 0f) format.frameRate else 60f
        val desiredRate = (baseFps * max(targetPlaybackSpeed, 1f)).coerceIn(60f, 240f)
        return if (superRate <= 0f) desiredRate else max(superRate, desiredRate)
    }

    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxValues: CodecMaxValues,
        codecOperatingRate: Float,
        deviceNeedsNoPostProcessWorkaround: Boolean,
        tunnelingAudioSessionId: Int
    ): MediaFormat {
        val mediaFormat = super.getMediaFormat(
            format,
            codecMimeType,
            codecMaxValues,
            codecOperatingRate,
            deviceNeedsNoPostProcessWorkaround,
            tunnelingAudioSessionId
        )
        // Request real-time hardware codec scheduling priority without enabling KEY_LOW_LATENCY
        // (which can break B-frame reordering in local MP4/MKV/WebM files).
        mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0)
        return mediaFormat
    }
}
