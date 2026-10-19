package com.example.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.HiddenMedia
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Computes a center-fit (letterbox / pillarbox) transform matrix for TextureView
 * so that even high-resolution videos (e.g. 1950x1200, 1200x1950, 4K) never overflow
 * or stretch inside the TextureView bounds.
 */
private fun applyCenterFitMatrix(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    videoAspectRatio: Float
) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoAspectRatio <= 0f) return
    val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
    val scaleX: Float
    val scaleY: Float
    if (videoAspectRatio > viewRatio) {
        scaleX = 1f
        scaleY = (viewRatio / videoAspectRatio).coerceIn(0.05f, 1f)
    } else {
        scaleX = (videoAspectRatio / viewRatio).coerceIn(0.05f, 1f)
        scaleY = 1f
    }
    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    textureView.setTransform(matrix)
    textureView.invalidate()
}

/**
 * Vintage / Classic Hi-Fi Media Player.
 * Features:
 * - Strict non-overlapping Column layout so the video viewport is always bounded
 *   between the Top Deck Bar and the Bottom Master Deck Console (never goes under UI controls)
 * - Exact aspect-fit calculation (width & height) for any resolution (e.g., 1950x1200, 1200x1950, 4K)
 * - Hardware TextureView Matrix scaling + rotation metadata awareness
 */
@Composable
fun VideoPlayerView(
    media: HiddenMedia,
    speed: Float,
    shuffleMode: Int,
    playedCount: Int,
    totalCount: Int,
    autoPlayNext: Boolean = true,
    loopVideo: Boolean = false,
    onSpeedChange: (Float) -> Unit,
    onShuffleToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onToggleLoop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    embeddedInTab: Boolean = false
) {
    val strings = LocalAppStrings.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var isPreparing by remember { mutableStateOf(true) }
    var activeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var surfaceRef by remember { mutableStateOf<Surface?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    // Video dimension & aspect ratio state (keyed on media so it resets cleanly on track change)
    var videoWidth by remember(media.id, media.internalPath) { mutableIntStateOf(0) }
    var videoHeight by remember(media.id, media.internalPath) { mutableIntStateOf(0) }
    var videoAspectRatio by remember(media.id, media.internalPath) { mutableFloatStateOf(16f / 9f) }

    // Visual action feedback when tapping screen (Play/Pause, +10s, -10s)
    var actionFeedback by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    LaunchedEffect(actionFeedback) {
        if (actionFeedback != null) {
            delay(650)
            actionFeedback = null
        }
    }

    // Simulated VU meter audio activity
    var vuLevelLeft by remember { mutableFloatStateOf(0.4f) }
    var vuLevelRight by remember { mutableFloatStateOf(0.45f) }

    // Initialize and handle MediaPlayer lifecycle
    DisposableEffect(media.id, media.internalPath) {
        isPreparing = true
        isPlaying = false
        currentPos = 0
        duration = 0

        // Extract accurate encoded dimensions & rotation metadata upfront
        var rawMetaWidth = 0
        var rawMetaHeight = 0
        var metaRotation = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(media.internalPath)
            rawMetaWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            rawMetaHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            metaRotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rawMetaWidth > 0 && rawMetaHeight > 0) {
                val dispW = if (metaRotation == 90 || metaRotation == 270) rawMetaHeight else rawMetaWidth
                val dispH = if (metaRotation == 90 || metaRotation == 270) rawMetaWidth else rawMetaHeight
                videoWidth = dispW
                videoHeight = dispH
                videoAspectRatio = (dispW.toFloat() / dispH.toFloat()).coerceIn(0.15f, 6.0f)
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        fun updateDimensionsFromPlayer(w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            // If metadata indicates 90/270 rotation and MediaPlayer reported unrotated raw dimensions, swap them
            val shouldSwap = (metaRotation == 90 || metaRotation == 270) &&
                rawMetaWidth > 0 && rawMetaHeight > 0 &&
                w == rawMetaWidth && h == rawMetaHeight
            val dispW = if (shouldSwap) h else w
            val dispH = if (shouldSwap) w else h
            videoWidth = dispW
            videoHeight = dispH
            val newRatio = (dispW.toFloat() / dispH.toFloat()).coerceIn(0.15f, 6.0f)
            videoAspectRatio = newRatio
            textureViewRef?.let { tv ->
                applyCenterFitMatrix(tv, tv.width, tv.height, newRatio)
            }
        }

        val player = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(media.internalPath)
                setOnVideoSizeChangedListener { _, width, height ->
                    updateDimensionsFromPlayer(width, height)
                }
                setOnPreparedListener { mp ->
                    activeMediaPlayer = mp
                    duration = mp.duration
                    isPreparing = false
                    if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                        updateDimensionsFromPlayer(mp.videoWidth, mp.videoHeight)
                    }
                    try {
                        val params = mp.playbackParams
                        params.speed = speed
                        mp.playbackParams = params
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    surfaceRef?.let { s ->
                        if (s.isValid) {
                            try {
                                mp.setSurface(s)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    try {
                        mp.start()
                        isPlaying = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                setOnCompletionListener {
                    if (loopVideo) {
                        try {
                            activeMediaPlayer?.seekTo(0)
                            activeMediaPlayer?.start()
                            isPlaying = true
                        } catch (e: Exception) {
                            isPlaying = false
                        }
                    } else if (autoPlayNext) {
                        isPlaying = false
                        onNext()
                    } else {
                        isPlaying = false
                    }
                }
                setOnErrorListener { _, _, _ ->
                    isPreparing = false
                    isPlaying = false
                    false
                }
                prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
                isPreparing = false
            }
        }
        activeMediaPlayer = player

        onDispose {
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (activeMediaPlayer == player) {
                activeMediaPlayer = null
            }
        }
    }

    // Auto-pause video when application goes into background to save resources and prevent audio leaks
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                activeMediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        try {
                            mp.pause()
                            isPlaying = false
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Keep track of playback speed updates reactively
    LaunchedEffect(speed, activeMediaPlayer, isPreparing) {
        if (!isPreparing) {
            activeMediaPlayer?.let { mp ->
                try {
                    val params = mp.playbackParams
                    params.speed = speed
                    mp.playbackParams = params
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Playback slider updates & simulated analog VU pulse
    LaunchedEffect(isPlaying, isPreparing) {
        if (isPlaying && !isPreparing) {
            var step = 0
            while (true) {
                activeMediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            currentPos = mp.currentPosition
                            step++
                            // Pulsing VU meter needles for vintage hi-fi feel
                            vuLevelLeft = (0.35f + (Math.sin(step * 0.4).toFloat() * 0.35f) + (Math.random().toFloat() * 0.2f)).coerceIn(0.1f, 0.95f)
                            vuLevelRight = (0.32f + (Math.cos(step * 0.45).toFloat() * 0.38f) + (Math.random().toFloat() * 0.2f)).coerceIn(0.1f, 0.95f)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                delay(200)
            }
        } else {
            vuLevelLeft = 0.05f
            vuLevelRight = 0.05f
        }
    }

    fun formatTime(ms: Int): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d", mins, secs)
    }

    val backgroundInteractionSource = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(VaultBackground)
            .clickable(
                interactionSource = backgroundInteractionSource,
                indication = null
            ) { /* Intercept clicks on background / margins to prevent falling through */ }
    ) {
        val isCompactHeight = maxHeight < 520.dp

        // Non-overlapping Column layout:
        // 1. Top Retro Deck Bar
        // 2. Middle Video Display Area (weight(1f), strictly clipped and aspect-fitted)
        // 3. Bottom Retro Master Deck Console
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Retro Deck Bar (Title + Vintage Badge + Resolution + Actions)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
                    .background(VaultBackground)
                    .then(if (!embeddedInTab) Modifier.statusBarsPadding() else Modifier)
                    .padding(
                        horizontal = 14.dp,
                        vertical = if (embeddedInTab || isCompactHeight) 6.dp else 10.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tactile Back Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VaultSurfaceElevated,
                        border = BorderStroke(1.dp, VaultBorder),
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { onClose() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = strings.back,
                                tint = VaultTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = media.originalFilename,
                            color = VaultTextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Vintage VHS / Cassette Tape label badge
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = VaultPrimaryDark.copy(alpha = 0.35f),
                                border = BorderStroke(0.8.dp, VaultPrimary)
                            ) {
                                Text(
                                    text = "CLASSIC DECK",
                                    color = VaultPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            if (videoWidth > 0 && videoHeight > 0) {
                                Text(
                                    text = "${videoWidth}×${videoHeight}",
                                    color = VaultAccentCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "${speed}x SPEED",
                                color = VaultTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Loop Toggle Button
                    if (onToggleLoop != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (loopVideo) VaultPrimary.copy(alpha = 0.25f) else VaultSurfaceElevated,
                            border = BorderStroke(1.dp, if (loopVideo) VaultPrimary else VaultBorder),
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { onToggleLoop() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.RepeatOne,
                                    contentDescription = strings.loopVideo,
                                    tint = if (loopVideo) VaultPrimary else VaultTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Delete Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VaultErrorContainer,
                        border = BorderStroke(1.dp, VaultError.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { onDelete() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = strings.delete,
                                tint = VaultError,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 2. Middle Video Display Area (Strictly bounded between Top Bar and Bottom Console)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .padding(horizontal = 10.dp, vertical = if (isCompactHeight) 4.dp else 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val availWidth = maxWidth
                val availHeight = maxHeight
                val safeRatio = videoAspectRatio.coerceIn(0.15f, 6.0f)

                // Compute exact (fittedWidth, fittedHeight) that fits inside (availWidth, availHeight)
                // without ever overflowing either width or height
                val (fittedWidth, fittedHeight) = remember(availWidth, availHeight, safeRatio) {
                    if (availWidth <= 0.dp || availHeight <= 0.dp) {
                        0.dp to 0.dp
                    } else {
                        val viewportRatio = availWidth / availHeight
                        if (safeRatio >= viewportRatio) {
                            // Video is wider than (or equal to) viewport: constrain by width
                            val w = availWidth
                            val h = (w / safeRatio).coerceAtMost(availHeight)
                            w to h
                        } else {
                            // Video is taller than viewport: constrain by height
                            val h = availHeight
                            val w = (h * safeRatio).coerceAtMost(availWidth)
                            w to h
                        }
                    }
                }

                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, VaultBorder),
                    modifier = Modifier
                        .size(width = fittedWidth, height = fittedHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .clipToBounds()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        activeMediaPlayer?.let { mp ->
                                            if (mp.isPlaying) {
                                                mp.pause()
                                                isPlaying = false
                                                actionFeedback = Icons.Default.Pause to strings.pause
                                            } else {
                                                mp.start()
                                                isPlaying = true
                                                actionFeedback = Icons.Default.PlayArrow to strings.play
                                            }
                                        }
                                    },
                                    onDoubleTap = { offset ->
                                        val isRightSide = offset.x > (size.width / 2)
                                        activeMediaPlayer?.let { mp ->
                                            val cur = mp.currentPosition
                                            val newPos = if (isRightSide) (cur + 10000).coerceAtMost(duration)
                                            else (cur - 10000).coerceAtLeast(0)
                                            mp.seekTo(newPos)
                                            currentPos = newPos
                                            actionFeedback = (if (isRightSide) Icons.Default.Forward10 else Icons.Default.Replay10) to (if (isRightSide) "+10s" else "-10s")
                                        }
                                    }
                                )
                            }
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).apply {
                                    textureViewRef = this
                                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                            applyCenterFitMatrix(this@apply, w, h, safeRatio)
                                            val surface = Surface(st)
                                            surfaceRef = surface
                                            activeMediaPlayer?.let { mp ->
                                                try {
                                                    mp.setSurface(surface)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }

                                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                            applyCenterFitMatrix(this@apply, w, h, safeRatio)
                                        }

                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                            activeMediaPlayer?.let { mp ->
                                                try {
                                                    mp.setSurface(null)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            surfaceRef?.release()
                                            surfaceRef = null
                                            if (textureViewRef === this@apply) {
                                                textureViewRef = null
                                            }
                                            return true
                                        }

                                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                    }
                                }
                            },
                            update = { tv ->
                                textureViewRef = tv
                                applyCenterFitMatrix(tv, tv.width, tv.height, safeRatio)
                                surfaceRef?.let { s ->
                                    if (s.isValid) {
                                        activeMediaPlayer?.let { mp ->
                                            try {
                                                mp.setSurface(s)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                        )

                        // Retro CRT Scanline subtle shading
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0x22000000),
                                            Color.Transparent,
                                            Color(0x22000000)
                                        )
                                    )
                                )
                        )

                        // Buffering Indicator
                        if (isPreparing) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = VaultSurfaceElevated.copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, VaultBorderSubtle),
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = VaultPrimary,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = strings.playerLoading,
                                        color = VaultPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // On-screen action feedback badge
                        androidx.compose.animation.AnimatedVisibility(
                            visible = actionFeedback != null,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            actionFeedback?.let { (icon, text) ->
                                Surface(
                                    shape = CircleShape,
                                    color = VaultBackground.copy(alpha = 0.8f),
                                    border = BorderStroke(1.5.dp, VaultPrimaryLight),
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(icon, contentDescription = null, tint = VaultPrimaryLight, modifier = Modifier.size(28.dp))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(text, color = VaultPrimaryLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Bottom Retro Master Deck Console (Strictly below the video viewport)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
                    .background(VaultSurface)
                    .border(BorderStroke(1.dp, VaultBorder))
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = if (isCompactHeight) 4.dp else 8.dp)
            ) {
                // VFD / LED Info Bar (Lamps + Dual Analog VU meters + Time readout)
                Surface(
                    color = Color(0xFF0C0E10),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF2E3440)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isCompactHeight) 2.dp else 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = if (isCompactHeight) 4.dp else 6.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Status Lamps (PLAY, PAUSE, STEREO)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Play LED
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlaying) RetroVfdGreen else Color(0xFF23352A))
                                )
                                Text(
                                    text = "PLAY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPlaying) RetroVfdGreen else Color(0xFF4B6354),
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Pause LED
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (!isPlaying && !isPreparing) RetroAmberGlow else Color(0xFF382B17))
                                )
                                Text(
                                    text = "PAUSE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isPlaying && !isPreparing) RetroAmberGlow else Color(0xFF6B532C),
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Stereo LED
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(RetroVfdGreen)
                                )
                                Text(
                                    text = "STEREO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetroVfdGreen,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Loop LED
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (loopVideo) VaultAccentCyan else Color(0xFF1B3238))
                                )
                                Text(
                                    text = "LOOP",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (loopVideo) VaultAccentCyan else Color(0xFF2C555E),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Mini VU Bar (Left & Right channel peak level)
                        Column(
                            modifier = Modifier.width(64.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // L Channel
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("L", fontSize = 7.sp, color = VaultTextMuted, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(Color(0xFF1E232A))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(vuLevelLeft)
                                            .fillMaxHeight()
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(RetroVfdGreen, RetroAmberGlow, VaultError)
                                                )
                                            )
                                    )
                                }
                            }
                            // R Channel
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("R", fontSize = 7.sp, color = VaultTextMuted, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(Color(0xFF1E232A))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(vuLevelRight)
                                            .fillMaxHeight()
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(RetroVfdGreen, RetroAmberGlow, VaultError)
                                                )
                                            )
                                    )
                                }
                            }
                        }

                        // Digital Fluorescent Counter display
                        Text(
                            text = "${formatTime(currentPos)} / ${formatTime(duration)}",
                            color = VaultAccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Seek Slider with Vintage Mechanical Grooved Track
                Slider(
                    value = currentPos.toFloat(),
                    onValueChange = { valPos ->
                        currentPos = valPos.toInt()
                        try {
                            activeMediaPlayer?.seekTo(valPos.toInt())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = VaultPrimary,
                        activeTrackColor = VaultPrimary,
                        inactiveTrackColor = VaultSurfaceHighlight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )

                // Tactile Vintage Tape Deck Controls (Shuffle, Prev, Play/Pause, Next, Speed Cycle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isCompactHeight) 2.dp else 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button (Mode 1 / Mode 2 / Off)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (shuffleMode > 0) VaultPrimary.copy(alpha = 0.25f) else VaultSurfaceElevated,
                        border = BorderStroke(1.2.dp, if (shuffleMode > 0) VaultPrimary else VaultBorder),
                        modifier = Modifier
                            .size(46.dp)
                            .clickable { onShuffleToggle() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = if (shuffleMode == 1) strings.shuffleRandom else if (shuffleMode == 2) strings.shuffleSmart else strings.shuffleOff,
                                tint = if (shuffleMode > 0) VaultPrimary else VaultTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            if (shuffleMode > 0) {
                                Text(
                                    text = "$shuffleMode",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VaultPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 4.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }

                    // Prev Track Deck Key
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VaultSurfaceElevated,
                        border = BorderStroke(1.2.dp, VaultBorder),
                        modifier = Modifier
                            .size(46.dp)
                            .clickable { onPrev() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = strings.prev, tint = VaultTextPrimary, modifier = Modifier.size(22.dp))
                        }
                    }

                    // Tactile Master Play/Pause Switch (Glowing Amber Key)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VaultPrimary,
                        border = BorderStroke(2.dp, VaultPrimaryLight),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .width(68.dp)
                            .height(48.dp)
                            .clickable {
                                activeMediaPlayer?.let { mp ->
                                    try {
                                        if (mp.isPlaying) {
                                            mp.pause()
                                            isPlaying = false
                                        } else {
                                            mp.start()
                                            isPlaying = true
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) strings.pause else strings.play,
                                tint = VaultOnPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Next Track Deck Key
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VaultSurfaceElevated,
                        border = BorderStroke(1.2.dp, VaultBorder),
                        modifier = Modifier
                            .size(46.dp)
                            .clickable { onNext() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipNext, contentDescription = strings.next, tint = VaultTextPrimary, modifier = Modifier.size(22.dp))
                        }
                    }

                    // Speed Cycle Key
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (speed > 1.0f) VaultPrimary.copy(alpha = 0.25f) else VaultSurfaceElevated,
                        border = BorderStroke(1.2.dp, if (speed > 1.0f) VaultPrimary else VaultBorder),
                        modifier = Modifier
                            .size(46.dp)
                            .clickable {
                                val nextSpeed = when (speed) {
                                    1.0f -> 1.25f
                                    1.25f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 3.0f
                                    3.0f -> 4.0f
                                    else -> 1.0f
                                }
                                onSpeedChange(nextSpeed)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${if (speed % 1.0f == 0.0f) speed.toInt() else speed}x",
                                color = if (speed > 1.0f) VaultPrimary else VaultTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Quick Pitch / Speed Selectors (Vintage Tape Speeds) - shown when height is not ultra-compact
                if (!isCompactHeight) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f).forEach { speedVal ->
                            val isSelected = speed == speedVal
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) VaultPrimary else VaultSurfaceElevated,
                                border = BorderStroke(0.8.dp, if (isSelected) VaultPrimaryLight else VaultBorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSpeedChange(speedVal) }
                            ) {
                                Text(
                                    text = "${if (speedVal % 1.0f == 0.0f) speedVal.toInt() else speedVal}x",
                                    color = if (isSelected) VaultOnPrimary else VaultTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
