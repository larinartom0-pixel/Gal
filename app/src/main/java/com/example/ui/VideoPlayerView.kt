package com.example.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.HiddenMedia
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale

/**
 * Minimalist, high-performance Video Player powered by AndroidX Media3 ExoPlayer.
 * Features:
 * - Robust hardware & software decoding for all MP4, WebM (VP8/VP9/AV1 + Opus/Vorbis), MKV, etc.
 * - Strict non-overlapping layout: video is bounded in a weight(1f) center area and scaled with
 *   AspectRatioFrameLayout.RESIZE_MODE_FIT so high-res videos (e.g. 1950x1200) NEVER go under UI bars.
 * - Pure rectangular styling (0dp corners) without rounded clipping.
 * - Minimal, distraction-free playback controls: Previous, Play/Pause, Next, Shuffle & Seekbar.
 * - Ultra-smooth hardware-accelerated playback speed control without dropped frames or stutter.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    media: HiddenMedia,
    speed: Float,
    shuffleMode: Int,
    playedCount: Int,
    totalCount: Int,
    autoPlayNext: Boolean = true,
    loopVideo: Boolean = false,
    useBrowserPlayer: Boolean = false,
    onTogglePlayerEngine: (() -> Unit)? = null,
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
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    var effectiveUseBrowser by remember { mutableStateOf(useBrowserPlayer) }
    LaunchedEffect(media.id, useBrowserPlayer) {
        effectiveUseBrowser = useBrowserPlayer
    }

    var isPlaybackActive by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    val browserController = remember { BrowserVideoController() }
    var showSpeedDialog by remember { mutableStateOf(false) }

    // Preserved timestamp & play-state when switching between Native <-> Browser mid-video
    var pendingResumePositionMs by remember(media.id) { mutableLongStateOf(0L) }
    var pendingAutoStart by remember(media.id) { mutableStateOf(true) }

    // Quick on-screen feedback (play/pause, speed, engine switch, or ±10s seek)
    var actionFeedback by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    LaunchedEffect(actionFeedback) {
        if (actionFeedback != null) {
            delay(650)
            actionFeedback = null
        }
    }

    // Media3 ExoPlayer setup & lifecycle (active only in Native mode; cleanly released in Browser mode)
    DisposableEffect(media.id, media.internalPath, effectiveUseBrowser) {
        if (effectiveUseBrowser) {
            exoPlayer = null
            return@DisposableEffect onDispose {}
        }

        isBuffering = true
        isPlaybackActive = pendingAutoStart

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 25_000,
                /* maxBufferMs = */ 65_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10_000, /* retainBackBufferFromKeyframe = */ true)
            .build()

        val renderersFactory = SmoothRenderersFactory(context.applicationContext)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val resumeMs = pendingResumePositionMs
        val autoStart = pendingAutoStart

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = autoStart
                repeatMode = if (loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                setSeekParameters(SeekParameters.EXACT)
                volume = 1f
                try {
                    playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 3.0f))
                } catch (_: Exception) {
                    playbackParameters = PlaybackParameters(1.0f)
                }

                val file = File(media.internalPath)
                val uri = Uri.fromFile(file)
                val mediaItem = MediaItem.fromUri(uri)
                setMediaItem(mediaItem)
                if (resumeMs > 0L) {
                    seekTo(resumeMs)
                }
                prepare()

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                isBuffering = true
                            }
                            Player.STATE_READY -> {
                                isBuffering = false
                                isPlaybackActive = this@apply.isPlaying
                            }
                            Player.STATE_ENDED -> {
                                isBuffering = false
                                isPlaybackActive = false
                                if (autoPlayNext && !loopVideo) {
                                    onNext()
                                }
                            }
                            Player.STATE_IDLE -> {
                                isBuffering = false
                                isPlaybackActive = false
                            }
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaybackActive = playing
                    }
                })
            }

        exoPlayer = player

        onDispose {
            player.stop()
            player.release()
            if (exoPlayer === player) {
                exoPlayer = null
            }
        }
    }

    // Seamlessly update playback speed on the fly without flushing decoder buffers or seeking
    LaunchedEffect(speed, exoPlayer) {
        exoPlayer?.let { player ->
            try {
                player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 3.0f))
            } catch (_: Exception) {
                player.playbackParameters = PlaybackParameters(1.0f)
            }
        }
    }

    // Keep ExoPlayer loop mode synced if toggled while playing
    LaunchedEffect(loopVideo, exoPlayer) {
        exoPlayer?.repeatMode = if (loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // Pause when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestUseBrowser by rememberUpdatedState(effectiveUseBrowser)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isPlaybackActive = false
                    exoPlayer?.pause()
                    if (latestUseBrowser) {
                        browserController.onLifecyclePause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (latestUseBrowser) {
                        browserController.onLifecycleResume()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun switchPlayerEngine() {
        val currentMs = if (effectiveUseBrowser) {
            browserController.currentPositionMs
        } else {
            exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        }
        pendingResumePositionMs = currentMs
        pendingAutoStart = isPlaybackActive
        isBuffering = true
        val switchingToBrowser = !effectiveUseBrowser
        effectiveUseBrowser = switchingToBrowser
        onTogglePlayerEngine?.invoke()
        actionFeedback = (if (switchingToBrowser) Icons.Default.Public else Icons.Default.PlayCircle) to
            (if (switchingToBrowser) strings.playerEngineBrowser else strings.playerEngineNative)
    }

    val backgroundInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VaultBackground)
            .clickable(
                interactionSource = backgroundInteraction,
                indication = null
            ) { /* Intercept outside touches */ }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP BAR: Sharp Rectangular Bar (Back, Filename, Counter, Delete)
            Surface(
                color = VaultSurface,
                shape = RectangleShape,
                border = BorderStroke(1.dp, VaultBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!embeddedInTab) Modifier.statusBarsPadding() else Modifier)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button (Crisp Rectangular)
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = VaultSurfaceElevated,
                        border = BorderStroke(1.dp, VaultBorder),
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { onClose() }
                            .testTag("player_back_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.back,
                                tint = VaultTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Title & Counter
                    Column(modifier = Modifier.weight(1f)) {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (totalCount > 0) {
                                Text(
                                    text = "$playedCount / $totalCount",
                                    color = VaultAccentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            val ext = media.originalFilename.substringAfterLast('.', "").uppercase()
                            if (ext.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(0.dp),
                                    color = VaultPrimaryDark.copy(alpha = 0.4f),
                                    border = BorderStroke(0.8.dp, VaultPrimary)
                                ) {
                                    Text(
                                        text = ext,
                                        color = VaultPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Player Engine Switch Button: Native (ExoPlayer) <-> Browser (WebView HTML5)
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = if (effectiveUseBrowser) VaultAccentCyan.copy(alpha = 0.2f) else VaultSurfaceElevated,
                        border = BorderStroke(1.dp, if (effectiveUseBrowser) VaultAccentCyan else VaultBorder),
                        modifier = Modifier
                            .height(38.dp)
                            .clickable { switchPlayerEngine() }
                            .testTag("player_engine_toggle_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (effectiveUseBrowser) Icons.Default.Public else Icons.Default.PlayCircle,
                                contentDescription = if (effectiveUseBrowser) strings.playerEngineBrowser else strings.playerEngineNative,
                                tint = if (effectiveUseBrowser) VaultAccentCyan else VaultTextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (effectiveUseBrowser) strings.playerEngineBrowserShort else strings.playerEngineNativeShort,
                                color = if (effectiveUseBrowser) VaultAccentCyan else VaultTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Loop Video Button (Crisp Rectangular)
                    if (onToggleLoop != null) {
                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = if (loopVideo) VaultPrimary.copy(alpha = 0.25f) else VaultSurfaceElevated,
                            border = BorderStroke(1.dp, if (loopVideo) VaultPrimary else VaultBorder),
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { onToggleLoop() }
                                .testTag("player_loop_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Repeat,
                                    contentDescription = strings.loopVideo,
                                    tint = if (loopVideo) VaultPrimary else VaultTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Delete Button (Crisp Rectangular)
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = VaultErrorContainer,
                        border = BorderStroke(1.dp, VaultError.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { onDelete() }
                            .testTag("player_delete_btn")
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

            // 2. VIDEO VIEWPORT: Bounded strictly between TopBar and BottomBar
            val currentPlaybackActiveState = rememberUpdatedState(isPlaybackActive)
            val currentExoPlayerState = rememberUpdatedState(exoPlayer)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val playing = currentPlaybackActiveState.value
                                if (latestUseBrowser) {
                                    if (playing) {
                                        isPlaybackActive = false
                                        browserController.pause()
                                        actionFeedback = Icons.Default.Pause to strings.pause
                                    } else {
                                        isPlaybackActive = true
                                        browserController.play()
                                        actionFeedback = Icons.Default.PlayArrow to strings.play
                                    }
                                } else {
                                    currentExoPlayerState.value?.let { player ->
                                        if (playing) {
                                            isPlaybackActive = false
                                            player.pause()
                                            actionFeedback = Icons.Default.Pause to strings.pause
                                        } else {
                                            isPlaybackActive = true
                                            player.play()
                                            actionFeedback = Icons.Default.PlayArrow to strings.play
                                        }
                                    }
                                }
                            },
                            onDoubleTap = { offset ->
                                val isRight = offset.x > (size.width / 2)
                                val delta = if (isRight) 10000L else -10000L
                                if (latestUseBrowser) {
                                    val dur = browserController.durationMs.coerceAtLeast(1L)
                                    val target = (browserController.currentPositionMs + delta).coerceIn(0L, dur)
                                    browserController.seekTo(target)
                                    actionFeedback = (if (isRight) Icons.Default.Forward10 else Icons.Default.Replay10) to (if (isRight) "+10s" else "-10s")
                                } else {
                                    currentExoPlayerState.value?.let { player ->
                                        val dur = player.duration.coerceAtLeast(1L)
                                        val target = (player.currentPosition + delta).coerceIn(0L, dur)
                                        player.seekTo(target)
                                        actionFeedback = (if (isRight) Icons.Default.Forward10 else Icons.Default.Replay10) to (if (isRight) "+10s" else "-10s")
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (effectiveUseBrowser) {
                    // Browser Mode: Chromium HTML5 <video> engine with byte-range streaming & 0.25x–3.0x speed
                    BrowserVideoPlayer(
                        mediaId = media.id,
                        mediaPath = media.internalPath,
                        speed = speed,
                        loopVideo = loopVideo,
                        initialPositionMs = pendingResumePositionMs,
                        autoStart = pendingAutoStart,
                        keepScreenOn = isPlaybackActive,
                        controller = browserController,
                        onBufferingChanged = { buffering -> isBuffering = buffering },
                        onIsPlayingChanged = { playing -> isPlaybackActive = playing },
                        onEnded = {
                            isBuffering = false
                            isPlaybackActive = false
                            if (autoPlayNext && !loopVideo) {
                                onNext()
                            }
                        },
                        onUnsupportedCodec = {
                            pendingResumePositionMs = browserController.currentPositionMs
                            pendingAutoStart = isPlaybackActive
                            effectiveUseBrowser = false
                            actionFeedback = Icons.Default.PlayCircle to strings.playerEngineNative
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Native Mode: ExoPlayer View (SurfaceView direct HWC rendering; isolated from timeline recompositions)
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setKeepContentOnPlayerReset(true)
                                keepScreenOn = isPlaybackActive
                                this.player = exoPlayer
                            }
                        },
                        update = { view ->
                            if (view.player !== exoPlayer) {
                                view.player = exoPlayer
                            }
                            if (view.keepScreenOn != isPlaybackActive) {
                                view.keepScreenOn = isPlaybackActive
                            }
                            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Buffering indicator (shown during loading or re-buffering)
                if (isBuffering) {
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = VaultSurfaceElevated.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, VaultBorder),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = VaultPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = strings.playerLoading,
                                color = VaultPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Action feedback badge (Play/Pause/±10s)
                androidx.compose.animation.AnimatedVisibility(
                    visible = actionFeedback != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    actionFeedback?.let { (icon, text) ->
                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = VaultBackground.copy(alpha = 0.85f),
                            border = BorderStroke(1.5.dp, VaultPrimary),
                            modifier = Modifier.size(70.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, contentDescription = null, tint = VaultPrimary, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text, color = VaultPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 3. BOTTOM CONTROL CONSOLE: Only the 4 essential buttons + Seekbar
            Surface(
                color = VaultSurface,
                shape = RectangleShape,
                border = BorderStroke(1.dp, VaultBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Isolated Timeline + Seekbar so 100ms progress ticks never recompose VideoPlayerView / PlayerView / WebView
                    PlayerTimelineSection(
                        useBrowserPlayer = effectiveUseBrowser,
                        exoPlayer = exoPlayer,
                        browserController = browserController,
                        speed = speed,
                        isPlaybackActive = isPlaybackActive,
                        onShowSpeedDialog = { showSpeedDialog = true }
                    )

                    // 5 Buttons in a Clean Row:
                    // 1. Previous | 2. Play/Pause | 3. Next | 4. Shuffle | 5. Speed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Previous Track
                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = VaultSurfaceElevated,
                            border = BorderStroke(1.dp, VaultBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clickable { onPrev() }
                                .testTag("player_prev_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = strings.prev,
                                    tint = VaultTextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // 2. Play / Pause (Dominant Action Button)
                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = VaultPrimary,
                            border = BorderStroke(1.5.dp, VaultPrimaryLight),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(46.dp)
                                .clickable {
                                    if (effectiveUseBrowser) {
                                        if (isPlaybackActive) {
                                            isPlaybackActive = false
                                            browserController.pause()
                                        } else {
                                            isPlaybackActive = true
                                            browserController.play()
                                        }
                                    } else {
                                        exoPlayer?.let { player ->
                                            if (isPlaybackActive) {
                                                isPlaybackActive = false
                                                player.pause()
                                            } else {
                                                isPlaybackActive = true
                                                player.play()
                                            }
                                        }
                                    }
                                }
                                .testTag("player_play_pause_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaybackActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaybackActive) strings.pause else strings.play,
                                    tint = VaultOnPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // 3. Next Track
                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = VaultSurfaceElevated,
                            border = BorderStroke(1.dp, VaultBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clickable { onNext() }
                                .testTag("player_next_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = strings.next,
                                    tint = VaultTextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // 4. Shuffle Toggle (Random playback order)
                        val isShuffleActive = shuffleMode > 0
                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = if (isShuffleActive) VaultPrimary.copy(alpha = 0.25f) else VaultSurfaceElevated,
                            border = BorderStroke(1.dp, if (isShuffleActive) VaultPrimary else VaultBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clickable { onShuffleToggle() }
                                .testTag("player_shuffle_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = if (isShuffleActive) strings.shuffleRandom else strings.shuffleOff,
                                    tint = if (isShuffleActive) VaultPrimary else VaultTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 5. Speed Button (Tap to open selector, Long press to quick cycle common speeds)
                        val speedDisplay = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
                        val isSpeedModified = speed != 1.0f

                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = if (isSpeedModified) VaultPrimary.copy(alpha = 0.2f) else VaultSurfaceElevated,
                            border = BorderStroke(1.dp, if (isSpeedModified) VaultPrimary else VaultBorder),
                            modifier = Modifier
                                .weight(1.15f)
                                .height(46.dp)
                                .pointerInput(speed) {
                                    detectTapGestures(
                                        onTap = { showSpeedDialog = true },
                                        onLongPress = {
                                            val cycleSpeeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
                                            val nextIndex = (cycleSpeeds.indexOf(speed) + 1) % cycleSpeeds.size
                                            val newSpeed = cycleSpeeds[nextIndex]
                                            onSpeedChange(newSpeed)
                                            actionFeedback = Icons.Default.FastForward to "⏩ ${newSpeed}x"
                                        }
                                    )
                                }
                                .testTag("player_speed_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FastForward,
                                            contentDescription = strings.speed,
                                            tint = if (isSpeedModified) VaultPrimary else VaultTextPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = speedDisplay,
                                            color = if (isSpeedModified) VaultPrimary else VaultTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = strings.speed.uppercase(),
                                        color = if (isSpeedModified) VaultPrimary else VaultTextSecondary,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Speed Selection Modal Dialog
        if (showSpeedDialog) {
            SpeedSelectorDialog(
                currentSpeed = speed,
                strings = strings,
                onSpeedSelected = { newSpeed ->
                    onSpeedChange(newSpeed)
                    actionFeedback = Icons.Default.FastForward to "⏩ ${newSpeed}x"
                },
                onDismiss = { showSpeedDialog = false }
            )
        }
    }
}

/**
 * Isolated timeline & seekbar composable that owns position polling state (`currentPos`, `duration`).
 * Supports both Native (ExoPlayer) and Browser (WebView HTML5) controllers while keeping 10Hz
 * progress ticks out of the parent [VideoPlayerView] scope.
 */
@Composable
private fun PlayerTimelineSection(
    useBrowserPlayer: Boolean,
    exoPlayer: ExoPlayer?,
    browserController: BrowserVideoController,
    speed: Float,
    isPlaybackActive: Boolean,
    onShowSpeedDialog: () -> Unit
) {
    var currentPos by remember(useBrowserPlayer, exoPlayer) { mutableLongStateOf(0L) }
    var duration by remember(useBrowserPlayer, exoPlayer) { mutableLongStateOf(0L) }
    var isUserSeeking by remember(useBrowserPlayer, exoPlayer) { mutableStateOf(false) }
    var seekSliderPos by remember(useBrowserPlayer, exoPlayer) { mutableFloatStateOf(0f) }

    // Update immediately on ExoPlayer seek discontinuities or ready transitions
    DisposableEffect(useBrowserPlayer, exoPlayer) {
        if (useBrowserPlayer) {
            return@DisposableEffect onDispose {}
        }
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = player.duration.coerceAtLeast(0L)
                    if (dur > 0L) duration = dur
                    if (!isUserSeeking) {
                        currentPos = player.currentPosition.coerceAtLeast(0L)
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (!isUserSeeking) {
                    currentPos = newPosition.positionMs.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(isPlaybackActive, useBrowserPlayer, exoPlayer) {
        while (isActive) {
            if (!isUserSeeking) {
                if (useBrowserPlayer) {
                    currentPos = browserController.currentPositionMs.coerceAtLeast(0L)
                    val dur = browserController.durationMs.coerceAtLeast(0L)
                    if (dur > 0L) {
                        duration = dur
                    }
                } else {
                    exoPlayer?.let { player ->
                        currentPos = player.currentPosition.coerceAtLeast(0L)
                        val dur = player.duration.coerceAtLeast(0L)
                        if (dur > 0L) {
                            duration = dur
                        }
                    }
                }
            }
            delay(100)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatPlayerTime(if (isUserSeeking) seekSliderPos.toLong() else currentPos),
            color = VaultAccentCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        if (speed != 1.0f) {
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = VaultPrimary.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, VaultPrimary),
                modifier = Modifier.clickable { onShowSpeedDialog() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = VaultPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${speed}x",
                        color = VaultPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Text(
            text = formatPlayerTime(duration),
            color = VaultTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }

    Slider(
        value = if (isUserSeeking) seekSliderPos else currentPos.toFloat(),
        onValueChange = { pos ->
            isUserSeeking = true
            seekSliderPos = pos
        },
        onValueChangeFinished = {
            isUserSeeking = false
            val targetMs = seekSliderPos.toLong()
            if (useBrowserPlayer) {
                browserController.seekTo(targetMs)
            } else {
                exoPlayer?.seekTo(targetMs)
            }
            currentPos = targetMs
        },
        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
        colors = SliderDefaults.colors(
            thumbColor = VaultPrimary,
            activeTrackColor = VaultPrimary,
            inactiveTrackColor = VaultSurfaceHighlight
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .testTag("player_seek_slider")
    )
}

private fun formatPlayerTime(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

/**
 * Minimalist, high-tech Speed selection dialog.
 * Allows picking forward speeds (0.25x - 3.0x) with hardware-accelerated smooth playback.
 */
@Composable
fun SpeedSelectorDialog(
    currentSpeed: Float,
    strings: com.example.ui.i18n.AppStrings,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val forwardSpeeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurfaceElevated,
        shape = RoundedCornerShape(0.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.speedSelectTitle.uppercase(),
                    color = VaultTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = VaultTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val chunked = forwardSpeeds.chunked(3)
                chunked.forEach { rowSpeeds ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowSpeeds.forEach { spd ->
                            val isSelected = currentSpeed == spd
                            val label = "${spd}x"
                            Surface(
                                shape = RoundedCornerShape(0.dp),
                                color = if (isSelected) VaultPrimary else VaultSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) VaultPrimaryLight else VaultBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clickable {
                                        onSpeedSelected(spd)
                                        onDismiss()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) VaultOnPrimary else VaultTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        repeat(3 - rowSpeeds.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = VaultSurface,
                border = BorderStroke(1.dp, VaultBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clickable {
                        onSpeedSelected(1.0f)
                        onDismiss()
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = strings.resetSpeed,
                        color = VaultAccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    )
}



