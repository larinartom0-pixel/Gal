package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.HiddenMedia
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import java.io.File

@Composable
fun PhotoDetailView(
    media: HiddenMedia,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    val backgroundInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(media.id) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VaultBackground)
            .clickable(
                interactionSource = backgroundInteractionSource,
                indication = null
            ) {}
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(media.internalPath))
                    .crossfade(true)
                    .build(),
                contentDescription = media.originalFilename,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
        }

        // Overlay Navigation Arrows (Sharp Vault Style)
        AnimatedVisibility(
            visible = showControls && scale == 1f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = VaultSurfaceElevated.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, VaultBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onPrev)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = strings.prev,
                            tint = VaultTextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = VaultSurfaceElevated.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, VaultBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onNext)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = strings.next,
                            tint = VaultTextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // Top Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                VaultBackground.copy(alpha = 0.95f),
                                VaultBackground.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlayerTopIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.back,
                        onClick = onClose
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
                        Text(
                            text = "${strings.badgePhoto} • ${media.size / 1024} KB",
                            color = VaultTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    PlayerTopIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = strings.delete,
                        onClick = onDelete,
                        containerColor = VaultErrorContainer,
                        borderColor = VaultError.copy(alpha = 0.6f),
                        tint = VaultError
                    )
                }
            }
        }

        // Bottom Zoom Hint
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = VaultSurfaceElevated.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, VaultBorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = VaultPrimaryLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = strings.photoZoomHint,
                        color = VaultTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
