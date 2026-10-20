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
import kotlinx.coroutines.delay
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

    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderPos by remember { mutableFloatStateOf(0f) }

    // Quick on-screen feedback (play/pause or ±10s seek)
    var actionFeedback by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    LaunchedEffect(actionFeedback) {
        if (actionFeedback != null) {
            delay(500)
            actionFeedback = null
        }
    }

    // Media3 ExoPlayer setup & lifecycle
    DisposableEffect(media.id, media.internalPath) {
        isBuffering = true
        isPlaying = false
        currentPos = 0L
        duration = 0L

        val player = ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = if (loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playbackParameters = PlaybackParameters(speed)

            val file = File(media.internalPath)
            val uri = Uri.fromFile(file)
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            isBuffering = true
                        }
                        Player.STATE_READY -> {
                            isBuffering = false
                            duration = this@apply.duration.coerceAtLeast(0L)
                            isPlaying = this@apply.isPlaying
                        }
                        Player.STATE_ENDED -> {
                            isBuffering = false
                            isPlaying = false
                            if (autoPlayNext && !loopVideo) {
                                onNext()
                            }
                        }
                        Player.STATE_IDLE -> {
                            isBuffering = false
                            isPlaying = false
                        }
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
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
            player.playbackParameters = PlaybackParameters(speed)
        }
    }

    // Pause when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Progress ticker
    LaunchedEffect(isPlaying, isBuffering) {
        while (true) {
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
                                    if (player.isPlaying) {
                                        player.pause()
                                        actionFeedback = Icons.Default.Pause to strings.pause
                                    } else {
                                        player.play()
                                        actionFeedback = Icons.Default.PlayArrow to strings.play
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

                // Buffering indicator
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
                    // Timeline: Current position / duration + Seek slider
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

                    // Exactly 4 Essential Buttons in a Clean Row:
                    // 1. Previous | 2. Play/Pause | 3. Next | 4. Shuffle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                .weight(1.4f)
                                .height(46.dp)
                                .clickable {
                                    exoPlayer?.let { player ->
                                        if (player.isPlaying) {
                                            player.pause()
                                        } else {
                                            player.play()
                                        }
                                    }
                                }
                                .testTag("player_play_pause_btn")
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = if (isShuffleActive) strings.shuffleRandom else strings.shuffleOff,
                                        tint = if (isShuffleActive) VaultPrimary else VaultTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
