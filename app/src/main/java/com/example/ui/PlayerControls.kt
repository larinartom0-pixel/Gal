package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@Composable
internal fun PlayerTopIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = VaultSurfaceElevated,
    borderColor: Color = VaultBorder,
    tint: Color = VaultTextPrimary
) {
    Surface(
        shape = RoundedCornerShape(0.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .size(40.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun PlayerControlTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = VaultSurfaceElevated,
    borderColor: Color = VaultBorder,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(0.dp),
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

/**
 * Isolated timeline & seekbar composable that owns position polling state (`currentPos`, `duration`).
 * Supports both Native (ExoPlayer) and Browser (WebView HTML5) controllers while keeping 10Hz
 * progress ticks out of the parent [VideoPlayerView] scope.
 */
@Composable
internal fun PlayerTimelineSection(
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
                    if (dur > 0L) duration = dur
                } else {
                    exoPlayer?.let { player ->
                        currentPos = player.currentPosition.coerceAtLeast(0L)
                        val dur = player.duration.coerceAtLeast(0L)
                        if (dur > 0L) duration = dur
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
                modifier = Modifier.clickable(onClick = onShowSpeedDialog)
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

internal fun formatPlayerTime(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

/**
 * Speed selection modal dialog for both Native (ExoPlayer) and Browser (WebView) playback.
 */
@Composable
fun SpeedSelectorDialog(
    currentSpeed: Float,
    strings: AppStrings,
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
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.close,
                        tint = VaultTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                forwardSpeeds.chunked(3).forEach { rowSpeeds ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowSpeeds.forEach { spd ->
                            val isSelected = currentSpeed == spd
                            Surface(
                                shape = RoundedCornerShape(0.dp),
                                color = if (isSelected) VaultPrimary else VaultSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) VaultPrimaryLight else VaultBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clickable {
                                        onSpeedSelected(spd)
                                        onDismiss()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${spd}x",
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
                    .height(40.dp)
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
