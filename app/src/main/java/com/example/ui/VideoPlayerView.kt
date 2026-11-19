package com.example.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import java.io.File

/**
 * Dual-engine Video Player supporting hardware-accelerated Native Media3 ExoPlayer
 * and an embedded Chromium HTML5 Browser player, with seamless mid-playback switching.
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
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onToggleLoop: (() -> Unit)? = null,
    modifier: Modifier = Modifier
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

    var pendingResumePositionMs by remember(media.id) { mutableLongStateOf(0L) }
    var pendingAutoStart by remember(media.id) { mutableStateOf(true) }

    var actionFeedback by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    LaunchedEffect(actionFeedback) {
        if (actionFeedback != null) {
            delay(650)
            actionFeedback = null
        }
    }

    DisposableEffect(media.id, media.internalPath, effectiveUseBrowser) {
        if (effectiveUseBrowser) {
            exoPlayer = null
            return@DisposableEffect onDispose {}
        }

        isBuffering = true
        isPlaybackActive = pendingAutoStart

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(25_000, 65_000, 1_000, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10_000, true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val resumeMs = pendingResumePositionMs
        val autoStart = pendingAutoStart

        val player = ExoPlayer.Builder(context, SmoothRenderersFactory(context.applicationContext))
            .setLoadControl(loadControl)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = autoStart
                repeatMode = if (loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                setSeekParameters(SeekParameters.EXACT)
                volume = 1f
                playbackParameters = runCatching {
                    PlaybackParameters(speed.coerceIn(0.25f, 3.0f))
                }.getOrDefault(PlaybackParameters(1.0f))

                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(media.internalPath))))
                if (resumeMs > 0L) seekTo(resumeMs)
                prepare()

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> isBuffering = true
                            Player.STATE_READY -> {
                                isBuffering = false
                                isPlaybackActive = this@apply.isPlaying
                            }
                            Player.STATE_ENDED -> {
                                isBuffering = false
                                isPlaybackActive = false
                                if (autoPlayNext && !loopVideo) onNext()
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
            if (exoPlayer === player) exoPlayer = null
        }
    }

    LaunchedEffect(speed, exoPlayer) {
        exoPlayer?.let { player ->
            player.playbackParameters = runCatching {
                PlaybackParameters(speed.coerceIn(0.25f, 3.0f))
            }.getOrDefault(PlaybackParameters(1.0f))
        }
    }

    LaunchedEffect(loopVideo, exoPlayer) {
        exoPlayer?.repeatMode = if (loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestUseBrowser by rememberUpdatedState(effectiveUseBrowser)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isPlaybackActive = false
                    exoPlayer?.pause()
                    if (latestUseBrowser) browserController.onLifecyclePause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (latestUseBrowser) browserController.onLifecycleResume()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
    val currentPlaybackActiveState = rememberUpdatedState(isPlaybackActive)
    val currentExoPlayerState = rememberUpdatedState(exoPlayer)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VaultBackground)
            .clickable(interactionSource = backgroundInteraction, indication = null) {}
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP BAR
            Surface(
                color = VaultSurface,
                shape = RectangleShape,
                border = BorderStroke(1.dp, VaultBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayerTopIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.back,
                        onClick = onClose,
                        modifier = Modifier.testTag("player_back_btn")
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.originalFilename,
                            color = VaultTextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        val ext = media.originalFilename.substringAfterLast('.', "").uppercase()
                        val metaLine = buildString {
                            if (totalCount > 0) append("$playedCount/$totalCount")
                            if (ext.isNotEmpty()) {
                                if (isNotEmpty()) append(" • ")
                                append(ext)
                            }
                        }
                        if (metaLine.isNotEmpty()) {
                            Text(
                                text = metaLine,
                                color = VaultAccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }

                    // Engine Switch Button: Native <-> Browser
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = if (effectiveUseBrowser) VaultAccentCyan.copy(alpha = 0.2f) else VaultSurfaceElevated,
                        border = BorderStroke(1.dp, if (effectiveUseBrowser) VaultAccentCyan else VaultBorder),
                        modifier = Modifier
                            .height(40.dp)
                            .clickable { switchPlayerEngine() }
                            .testTag("player_engine_toggle_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
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

                    if (onToggleLoop != null) {
                        PlayerTopIconButton(
                            icon = Icons.Default.Repeat,
                            contentDescription = strings.loopVideo,
                            onClick = onToggleLoop,
                            containerColor = if (loopVideo) VaultPrimary.copy(alpha = 0.25f) else VaultSurfaceElevated,
                            borderColor = if (loopVideo) VaultPrimary else VaultBorder,
                            tint = if (loopVideo) VaultPrimary else VaultTextSecondary,
                            modifier = Modifier.testTag("player_loop_btn")
                        )
                    }

                    PlayerTopIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = strings.delete,
                        onClick = onDelete,
                        containerColor = VaultErrorContainer,
                        borderColor = VaultError.copy(alpha = 0.6f),
                        tint = VaultError,
                        modifier = Modifier.testTag("player_delete_btn")
                    )
                }
            }

            // 2. VIDEO VIEWPORT
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
                                val delta = if (isRight) 10_000L else -10_000L
                                val feedback = (if (isRight) Icons.Default.Forward10 else Icons.Default.Replay10) to
                                    (if (isRight) "+10s" else "-10s")
                                if (latestUseBrowser) {
                                    val dur = browserController.durationMs.coerceAtLeast(1L)
                                    val target = (browserController.currentPositionMs + delta).coerceIn(0L, dur)
                                    browserController.seekTo(target)
                                    actionFeedback = feedback
                                } else {
                                    currentExoPlayerState.value?.let { player ->
                                        val dur = player.duration.coerceAtLeast(1L)
                                        val target = (player.currentPosition + delta).coerceIn(0L, dur)
                                        player.seekTo(target)
                                        actionFeedback = feedback
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (effectiveUseBrowser) {
                    BrowserVideoPlayer(
                        mediaId = media.id,
                        mediaPath = media.internalPath,
                        speed = speed,
                        loopVideo = loopVideo,
                        initialPositionMs = pendingResumePositionMs,
                        autoStart = pendingAutoStart,
                        keepScreenOn = isPlaybackActive,
                        controller = browserController,
                        onBufferingChanged = { isBuffering = it },
                        onIsPlayingChanged = { isPlaybackActive = it },
                        onEnded = {
                            isBuffering = false
                            isPlaybackActive = false
                            if (autoPlayNext && !loopVideo) onNext()
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
                                player = exoPlayer
                            }
                        },
                        update = { view ->
                            if (view.player !== exoPlayer) view.player = exoPlayer
                            if (view.keepScreenOn != isPlaybackActive) view.keepScreenOn = isPlaybackActive
                            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

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
                                modifier = Modifier.size(18.dp)
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
                            modifier = Modifier.sizeIn(minWidth = 72.dp, minHeight = 72.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = VaultPrimary, modifier = Modifier.size(26.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text, color = VaultPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. BOTTOM CONTROL CONSOLE
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
                    PlayerTimelineSection(
                        useBrowserPlayer = effectiveUseBrowser,
                        exoPlayer = exoPlayer,
                        browserController = browserController,
                        speed = speed,
                        isPlaybackActive = isPlaybackActive,
                        onShowSpeedDialog = { showSpeedDialog = true }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerControlTile(
                            onClick = onPrev,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("player_prev_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = strings.prev,
                                tint = VaultTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        PlayerControlTile(
                            onClick = {
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
                            },
                            containerColor = VaultPrimary,
                            borderColor = VaultPrimaryLight,
                            borderWidth = 1.5.dp,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("player_play_pause_btn")
                        ) {
                            Icon(
                                imageVector = if (isPlaybackActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaybackActive) strings.pause else strings.play,
                                tint = VaultOnPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        PlayerControlTile(
                            onClick = onNext,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("player_next_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = strings.next,
                                tint = VaultTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        val isShuffleActive = shuffleMode > 0
                        PlayerControlTile(
                            onClick = onShuffleToggle,
                            containerColor = if (isShuffleActive) VaultPrimary.copy(alpha = 0.25f) else VaultSurfaceElevated,
                            borderColor = if (isShuffleActive) VaultPrimary else VaultBorder,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("player_shuffle_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = if (isShuffleActive) strings.shuffleRandom else strings.shuffleOff,
                                tint = if (isShuffleActive) VaultPrimary else VaultTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        val speedDisplay = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
                        val isSpeedModified = speed != 1.0f
                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = if (isSpeedModified) VaultPrimary.copy(alpha = 0.2f) else VaultSurfaceElevated,
                            border = BorderStroke(1.dp, if (isSpeedModified) VaultPrimary else VaultBorder),
                            modifier = Modifier
                                .weight(1.15f)
                                .height(48.dp)
                                .pointerInput(speed) {
                                    detectTapGestures(
                                        onTap = { showSpeedDialog = true },
                                        onLongPress = {
                                            val cycleSpeeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
                                            val nextIndex = (cycleSpeeds.indexOf(speed) + 1) % cycleSpeeds.size
                                            val newSpeed = cycleSpeeds[nextIndex]
                                            onSpeedChange(newSpeed)
                                            actionFeedback = Icons.Default.FastForward to "${newSpeed}x"
                                        }
                                    )
                                }
                                .testTag("player_speed_btn")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
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

        if (showSpeedDialog) {
            SpeedSelectorDialog(
                currentSpeed = speed,
                strings = strings,
                onSpeedSelected = { newSpeed ->
                    onSpeedChange(newSpeed)
                    actionFeedback = Icons.Default.FastForward to "${newSpeed}x"
                },
                onDismiss = { showSpeedDialog = false }
            )
        }
    }
}
