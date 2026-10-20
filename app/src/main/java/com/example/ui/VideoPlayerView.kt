package com.example.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.HiddenMedia
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
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

    var isPlaybackActive by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderPos by remember { mutableFloatStateOf(0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    val seekSignal = remember { Channel<Unit>(Channel.CONFLATED) }

    // Quick on-screen feedback (play/pause, speed/reverse, or ±10s seek)
    var actionFeedback by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    LaunchedEffect(actionFeedback) {
        if (actionFeedback != null) {
            delay(650)
            actionFeedback = null
        }
    }

    // Media3 ExoPlayer setup & lifecycle
    DisposableEffect(media.id, media.internalPath) {
        isBuffering = true
        isPlaybackActive = true
        currentPos = 0L
        duration = 0L

        val isReverse = speed < 0f
        val player = ExoPlayer.Builder(context).build().apply {
            playWhenReady = !isReverse
            repeatMode = if (loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            if (isReverse) {
                volume = 0f
            } else {
                volume = 1f
                try {
                    playbackParameters = PlaybackParameters(speed.coerceIn(0.1f, 4.0f))
                } catch (_: Exception) {
                    playbackParameters = PlaybackParameters(1.0f)
                }
            }

            val file = File(media.internalPath)
            val uri = Uri.fromFile(file)
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            if (speed >= 0f) {
                                isBuffering = true
                            }
                        }
                        Player.STATE_READY -> {
                            isBuffering = false
                            duration = this@apply.duration.coerceAtLeast(0L)
                            if (speed > 0f) {
                                isPlaybackActive = this@apply.isPlaying
                            }
                            seekSignal.trySend(Unit)
                        }
                        Player.STATE_ENDED -> {
                            isBuffering = false
                            if (speed > 0f) {
                                isPlaybackActive = false
                                if (autoPlayNext && !loopVideo) {
                                    onNext()
                                }
                            }
                        }
                        Player.STATE_IDLE -> {
                            isBuffering = false
                            isPlaybackActive = false
                        }
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    if (speed > 0f) {
                        isPlaybackActive = playing
                    }
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

    // React to playback speed changes
    LaunchedEffect(speed, exoPlayer) {
        exoPlayer?.let { player ->
            if (speed > 0f) {
                player.volume = 1f
                try {
                    player.playbackParameters = PlaybackParameters(speed.coerceIn(0.1f, 4.0f))
                } catch (_: Exception) {
                    player.playbackParameters = PlaybackParameters(1.0f)
                }
                if (isPlaybackActive && !player.isPlaying) {
                    player.play()
                }
            } else {
                // In reverse mode, pause ExoPlayer's forward audio and decoding loop
                player.pause()
                player.volume = 0f
            }
        }
    }

    // Pause when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                isPlaybackActive = false
                exoPlayer?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Reverse Playback Coroutine Engine (smoothly seeks backwards frame by frame with decode pacing)
    LaunchedEffect(speed, isPlaybackActive, loopVideo, duration, exoPlayer) {
        if (speed < 0f && isPlaybackActive) {
            val player = exoPlayer ?: return@LaunchedEffect
            player.pause()
            player.volume = 0f

            // If at beginning or close to 0, auto jump to the end of the video to play backwards
            var currentReversePos = player.currentPosition
            if (currentReversePos <= 100L && duration > 0L) {
                currentReversePos = duration
                player.seekTo(duration)
                currentPos = duration
                withTimeoutOrNull(250L) {
                    seekSignal.receive()
                }
            }

            val absSpeed = kotlin.math.abs(speed).coerceIn(0.25f, 4.0f)

            // Pacing delay: allows each decoded frame to be rendered and viewed on screen
            // without being cancelled or dropped by rapid subsequent seeks.
            val pacingDelayMs = when {
                absSpeed <= 0.5f -> 80L
                absSpeed <= 1.0f -> 60L
                absSpeed <= 2.0f -> 50L
                else -> 40L
            }

            while (isActive && isPlaybackActive && speed < 0f) {
                if (isUserSeeking) {
                    currentReversePos = currentPos
                    delay(50)
                    continue
                }

                // If user jumped via double tap (+/-10s) or external seek
                if (kotlin.math.abs(currentReversePos - player.currentPosition) > 1500L) {
                    currentReversePos = player.currentPosition
                }

                // Drain any old seek signal before initiating new seek
                while (seekSignal.tryReceive().isSuccess) { /* drain */ }

                // Dynamic step based on cycle duration (pacing + decode time) to maintain real-time speed
                val nominalCycleMs = pacingDelayMs + 40L
                val stepMs = (nominalCycleMs * absSpeed).toLong().coerceAtLeast(35L)
                val nextPos = currentReversePos - stepMs

                if (nextPos <= 0L) {
                    if (loopVideo && duration > 0L) {
                        currentReversePos = duration
                        player.seekTo(duration)
                        currentPos = duration
                        withTimeoutOrNull(250L) {
                            seekSignal.receive()
                        }
                        delay(pacingDelayMs)
                        continue
                    } else {
                        player.seekTo(0L)
                        currentPos = 0L
                        isPlaybackActive = false
                        break
                    }
                } else {
                    currentReversePos = nextPos
                    player.seekTo(nextPos)
                    currentPos = nextPos

                    // Wait for ExoPlayer to signal STATE_READY (frame rendered)
                    // with a tight timeout so it never blocks or hangs
                    withTimeoutOrNull(100L) {
                        seekSignal.receive()
                    }

                    // Pacing delay so the frame is perceived smoothly
                    delay(pacingDelayMs)
                }
            }
        }
    }

    // Forward Playback Progress ticker
    LaunchedEffect(isPlaybackActive, speed, exoPlayer) {
        if (speed > 0f) {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (!isUserSeeking) {
                        currentPos = player.currentPosition.coerceAtLeast(0L)
                        val dur = player.duration.coerceAtLeast(0L)
                        if (dur > 0L) {
                            duration = dur
                        }
                    }
                }
                delay(200)
            }
        }
    }

    fun formatTime(ms: Long): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
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
            // AspectRatioFrameLayout.RESIZE_MODE_FIT guarantees large resolutions (e.g. 1950x1200)
            // scale perfectly within bounds without ever overflowing or hiding under UI controls.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                exoPlayer?.let { player ->
                                    if (isPlaybackActive) {
                                        isPlaybackActive = false
                                        player.pause()
                                        actionFeedback = Icons.Default.Pause to strings.pause
                                    } else {
                                        isPlaybackActive = true
                                        if (speed > 0f) {
                                            player.play()
                                        }
                                        actionFeedback = (if (speed < 0f) Icons.Default.FastRewind else Icons.Default.PlayArrow) to 
                                            (if (speed < 0f) strings.reverseSpeed else strings.play)
                                    }
                                }
                            },
                            onDoubleTap = { offset ->
                                val isRight = offset.x > (size.width / 2)
                                exoPlayer?.let { player ->
                                    val delta = if (isRight) 10000L else -10000L
                                    val target = (player.currentPosition + delta).coerceIn(0L, duration.coerceAtLeast(1L))
                                    player.seekTo(target)
                                    currentPos = target
                                    actionFeedback = (if (isRight) Icons.Default.Forward10 else Icons.Default.Replay10) to (if (isRight) "+10s" else "-10s")
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // ExoPlayer View
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = exoPlayer
                        }
                    },
                    update = { view ->
                        if (view.player !== exoPlayer) {
                            view.player = exoPlayer
                        }
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Buffering indicator (only shown for forward playback or initial load)
                if (isBuffering && speed >= 0f) {
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
                    // Timeline: Current position / duration + Speed Badge + Seek slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(if (isUserSeeking) seekSliderPos.toLong() else currentPos),
                            color = VaultAccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        // Visual Badge when Speed != 1.0 or Reverse active
                        if (speed < 0f) {
                            Surface(
                                shape = RoundedCornerShape(0.dp),
                                color = VaultError.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, VaultError),
                                modifier = Modifier.clickable { showSpeedDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = null,
                                        tint = VaultError,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${strings.reverseSpeed.uppercase()} ${speed}x",
                                        color = VaultError,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        } else if (speed != 1.0f) {
                            Surface(
                                shape = RoundedCornerShape(0.dp),
                                color = VaultPrimary.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, VaultPrimary),
                                modifier = Modifier.clickable { showSpeedDialog = true }
                            ) {
                                Text(
                                    text = "⏩ ${speed}x",
                                    color = VaultPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = formatTime(duration),
                            color = VaultTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Seek Slider (Crisp rectangular thumb/track)
                    Slider(
                        value = if (isUserSeeking) seekSliderPos else currentPos.toFloat(),
                        onValueChange = { pos ->
                            isUserSeeking = true
                            seekSliderPos = pos
                        },
                        onValueChangeFinished = {
                            isUserSeeking = false
                            exoPlayer?.seekTo(seekSliderPos.toLong())
                            currentPos = seekSliderPos.toLong()
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

                    // 5 Buttons in a Clean Row:
                    // 1. Previous | 2. Play/Pause | 3. Next | 4. Shuffle | 5. Speed & Reverse
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
                                    exoPlayer?.let { player ->
                                        if (isPlaybackActive) {
                                            isPlaybackActive = false
                                            player.pause()
                                        } else {
                                            isPlaybackActive = true
                                            if (speed > 0f) {
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

                        // 5. Speed & Reverse Button (Tap to open selector, Long press to toggle direction)
                        val isReverse = speed < 0f
                        val absSpeed = kotlin.math.abs(speed)
                        val speedDisplay = if (absSpeed % 1f == 0f) "${absSpeed.toInt()}x" else "${absSpeed}x"

                        Surface(
                            shape = RoundedCornerShape(0.dp),
                            color = if (isReverse) VaultError.copy(alpha = 0.2f) else if (speed != 1.0f) VaultPrimary.copy(alpha = 0.2f) else VaultSurfaceElevated,
                            border = BorderStroke(1.dp, if (isReverse) VaultError else if (speed != 1.0f) VaultPrimary else VaultBorder),
                            modifier = Modifier
                                .weight(1.15f)
                                .height(46.dp)
                                .pointerInput(speed) {
                                    detectTapGestures(
                                        onTap = { showSpeedDialog = true },
                                        onLongPress = {
                                            // Toggle direction between forward and reverse
                                            val newSpeed = -speed
                                            onSpeedChange(newSpeed)
                                            actionFeedback = (if (newSpeed < 0f) Icons.Default.FastRewind else Icons.Default.FastForward) to 
                                                (if (newSpeed < 0f) "⏪ ${strings.reverseSpeed}" else "⏩ ${strings.normalSpeed}")
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
                                        if (isReverse) {
                                            Icon(
                                                imageVector = Icons.Default.FastRewind,
                                                contentDescription = strings.reverseSpeed,
                                                tint = VaultError,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = if (isReverse) "-$speedDisplay" else speedDisplay,
                                            color = if (isReverse) VaultError else if (speed != 1.0f) VaultPrimary else VaultTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = if (isReverse) strings.reverseSpeed.uppercase() else strings.speed.uppercase(),
                                        color = if (isReverse) VaultError.copy(alpha = 0.85f) else VaultTextSecondary,
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

        // Speed & Reverse Modal Dialog
        if (showSpeedDialog) {
            SpeedSelectorDialog(
                currentSpeed = speed,
                strings = strings,
                onSpeedSelected = { newSpeed ->
                    onSpeedChange(newSpeed)
                    val isRev = newSpeed < 0f
                    actionFeedback = (if (isRev) Icons.Default.FastRewind else Icons.Default.FastForward) to 
                        (if (isRev) "⏪ ${strings.reverseSpeed} ${newSpeed}x" else "⏩ ${newSpeed}x")
                },
                onDismiss = { showSpeedDialog = false }
            )
        }
    }
}

/**
 * Minimalist, high-tech Speed & Reverse selection dialog.
 * Allows picking forward speeds (0.25x - 3.0x) or reverse playback speeds (-0.25x - -3.0x).
 */
@Composable
fun SpeedSelectorDialog(
    currentSpeed: Float,
    strings: com.example.ui.i18n.AppStrings,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var isReverseTab by remember { mutableStateOf(currentSpeed < 0f) }

    val forwardSpeeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)
    val reverseSpeeds = listOf(-0.25f, -0.5f, -0.75f, -1.0f, -1.25f, -1.5f, -2.0f, -3.0f)

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Direction Toggle Tabs (Forward vs Reverse)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Forward Tab
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = if (!isReverseTab) VaultPrimary else VaultSurface,
                        border = BorderStroke(1.dp, if (!isReverseTab) VaultPrimaryLight else VaultBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable { isReverseTab = false }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.FastForward,
                                    contentDescription = null,
                                    tint = if (!isReverseTab) VaultOnPrimary else VaultTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = strings.normalSpeed,
                                    color = if (!isReverseTab) VaultOnPrimary else VaultTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Reverse Tab
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = if (isReverseTab) VaultError else VaultSurface,
                        border = BorderStroke(1.dp, if (isReverseTab) VaultError else VaultBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable { isReverseTab = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.FastRewind,
                                    contentDescription = null,
                                    tint = if (isReverseTab) Color.White else VaultTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = strings.reverseSpeed,
                                    color = if (isReverseTab) Color.White else VaultTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Grid of Speed Chips (3 per row)
                val targetSpeeds = if (isReverseTab) reverseSpeeds else forwardSpeeds
                val chunked = targetSpeeds.chunked(3)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                    color = if (isSelected) {
                                        if (spd < 0f) VaultError else VaultPrimary
                                    } else VaultSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) {
                                            if (spd < 0f) VaultError else VaultPrimaryLight
                                        } else VaultBorder
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
                                            color = if (isSelected) {
                                                if (spd < 0f) Color.White else VaultOnPrimary
                                            } else VaultTextPrimary,
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

