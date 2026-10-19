package com.example.ui

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.VideoThumbnailManager
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance smart Video Cover image component:
 * 1. Checks disk cache first for instant retrieval.
 * 2. If missing, extracts the middle frame (smart black-level avoidance).
 * 3. Saves to cache persistently across app launches.
 * 4. Renders with retro CRT bezel / cassette reel aesthetics.
 */
@Composable
fun VideoCoverThumbnail(
    videoPath: String,
    fileSize: Long,
    lastModified: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    showPlayOverlay: Boolean = false,
    playIconSize: Dp = 20.dp,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var coverFile by remember(videoPath, fileSize, lastModified) {
        mutableStateOf<File?>(
            VideoThumbnailManager.getCachedCoverFile(context, videoPath, fileSize, lastModified)
        )
    }
    var isLoading by remember(videoPath, fileSize, lastModified) {
        mutableStateOf(coverFile == null)
    }

    LaunchedEffect(videoPath, fileSize, lastModified) {
        if (coverFile == null) {
            isLoading = true
            val file = VideoThumbnailManager.getOrGenerateCoverFile(
                context = context,
                videoPath = videoPath,
                fileSize = fileSize,
                lastModified = lastModified
            )
            coverFile = file
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(VaultSurfaceElevated)
            .border(1.dp, VaultBorderSubtle, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (coverFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverFile)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isLoading) {
            // Subtle vintage loading indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = VaultPrimary,
                    strokeWidth = 2.dp
                )
            }
        } else {
            // Fallback icon if file unreadable or failed
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = contentDescription,
                    tint = VaultTextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Retro scanline overlay & play badge
        if (showPlayOverlay) {
            val strings = LocalAppStrings.current
            Surface(
                shape = CircleShape,
                color = VaultBackground.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
                modifier = Modifier.size(playIconSize + 14.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = strings.play,
                        tint = VaultPrimary,
                        modifier = Modifier.size(playIconSize)
                    )
                }
            }
        }
    }
}
