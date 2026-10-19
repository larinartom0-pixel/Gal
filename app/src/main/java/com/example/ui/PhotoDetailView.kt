package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    embeddedInTab: Boolean = false
) {
    val strings = LocalAppStrings.current
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    val backgroundInteractionSource = remember { MutableInteractionSource() }

    // Reset zoom on file shift (Next/Prev)
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
            ) { /* Intercept background taps to prevent falling through to list underneath */ }
    ) {
        // Main Photo Content with Double-Tap, Single-Tap and Multi-Touch Support (Zoom/Pinch and Pan)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
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

        // Overlay Navigation Arrows
        AnimatedVisibility(
            visible = showControls && scale == 1f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrev,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(VaultSurfaceElevated.copy(alpha = 0.8f))
                        .border(1.dp, VaultBorderSubtle, CircleShape)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = strings.prev,
                        tint = VaultTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(VaultSurfaceElevated.copy(alpha = 0.8f))
                        .border(1.dp, VaultBorderSubtle, CircleShape)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = strings.next,
                        tint = VaultTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Modern Frosted Header Controls Bar
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
                                VaultBackground.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
                    .then(if (!embeddedInTab) Modifier.statusBarsPadding() else Modifier)
                    .padding(horizontal = 16.dp, vertical = if (embeddedInTab) 6.dp else 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(VaultSurfaceElevated.copy(alpha = 0.85f))
                            .border(1.dp, VaultBorderSubtle, CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back, tint = VaultTextPrimary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = media.originalFilename,
                            color = VaultTextPrimary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = VaultPrimary.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, VaultPrimary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = strings.badgePhoto,
                                    color = VaultPrimaryLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = "${(media.size / 1024)} KB",
                                color = VaultTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(VaultErrorContainer)
                            .border(1.dp, VaultError.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = VaultError)
                    }
                }
            }
        }

        // Hint bottom pill banner
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
                shape = RoundedCornerShape(20.dp),
                color = VaultSurfaceElevated.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorderSubtle)
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

