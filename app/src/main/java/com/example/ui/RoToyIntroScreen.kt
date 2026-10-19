package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cinematic Company Intro screen for "roTOY".
 * 
 * Features:
 * - Animated futuristic/retro mechanical toy logo with rotating HUD elements, glowing core and soundwaves.
 * - Auto-advances after intro duration.
 * - Instantly skippable at any time by tapping anywhere on the screen or clicking the skip button.
 * - System back button cleanly skips the intro.
 */
@Composable
fun RoToyIntroScreen(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    // Back button immediately skips
    BackHandler {
        onSkip()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                onSkip()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Animation transition timeline
    val transition = rememberInfiniteTransition(label = "rotoy_infinite")

    // Rotation of outer HUD rings
    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    val counterRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_rotation"
    )

    // Glow pulse for logo
    val glowPulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    // Animated entrance progress (0f to 1f)
    val entranceAnim = remember { Animatable(0f) }
    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Step 1: Smooth reveal of logo
        entranceAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
        // Step 2: Linear timer bar over ~3.5 seconds
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3200, easing = LinearEasing)
        )
        // Auto-skip when timer finishes
        delay(200)
        onSkip()
    }

    // Full screen tap to skip
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF222630),
                        Color(0xFF121316),
                        Color(0xFF0A0A0C)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onSkip()
            }
            .testTag("rotoy_intro_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background circuit grid / radar lines
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.18f)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.75f

            // Radar circles
            drawCircle(
                color = VaultPrimaryLight,
                radius = maxR * 0.4f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = VaultAccentCyan,
                radius = maxR * 0.68f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = VaultPrimary,
                radius = maxR * 0.95f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Crosshair lines
            drawLine(
                color = VaultBorderSubtle,
                start = Offset(center.x, center.y - maxR),
                end = Offset(center.x, center.y + maxR),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = VaultBorderSubtle,
                start = Offset(center.x - maxR, center.y),
                end = Offset(center.x + maxR, center.y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Top skip button affordance
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VaultSurfaceElevated.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSkip() }
                    .testTag("intro_skip_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = strings.introSkip,
                        color = VaultTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = strings.introSkipDesc,
                        tint = VaultPrimaryLight,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Center Animated Content (Logo + Typography + Soundwave)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .scale(0.85f + 0.15f * entranceAnim.value)
                .alpha(entranceAnim.value)
        ) {
            // Stylized Animated Logo Emblem
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                // Animated Custom Canvas HUD & Emblem
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f - 14.dp.toPx()

                    // Outer rotating segment ring
                    rotate(ringRotation, center) {
                        for (i in 0 until 12) {
                            val angleRad = Math.toRadians((i * 30).toDouble())
                            val startX = center.x + (radius - 8.dp.toPx()) * cos(angleRad).toFloat()
                            val startY = center.y + (radius - 8.dp.toPx()) * sin(angleRad).toFloat()
                            val endX = center.x + radius * cos(angleRad).toFloat()
                            val endY = center.y + radius * sin(angleRad).toFloat()

                            drawLine(
                                color = if (i % 3 == 0) VaultPrimaryLight else VaultPrimary.copy(alpha = 0.4f),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = if (i % 3 == 0) 3.dp.toPx() else 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Inner counter-rotating dashed ring
                    rotate(counterRotation, center) {
                        drawCircle(
                            color = VaultAccentCyan.copy(alpha = 0.5f),
                            radius = radius * 0.75f,
                            center = center,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Glowing center orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                VaultPrimaryLight.copy(alpha = 0.35f * glowPulse),
                                VaultPrimaryDark.copy(alpha = 0.1f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius * 0.7f
                        ),
                        radius = radius * 0.7f,
                        center = center
                    )
                }

                // Center Icon: Mechanical Robot Toy / Futuristic Emblem
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = VaultSurface,
                    border = androidx.compose.foundation.BorderStroke(2.dp, VaultPrimary.copy(alpha = 0.8f * glowPulse)),
                    modifier = Modifier.size(86.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Robot Toy Head & Antenna
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp, 10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(VaultAccentCyan)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp, 10.dp)
                                        .clip(CircleShape)
                                        .background(VaultPrimaryLight)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp, 10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(VaultAccentCyan)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Visor glow
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10141A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, VaultAccentCyan.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .height(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(VaultPrimaryLight)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(VaultAccentCyan)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // COMPANY NAME: "roTOY"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ro",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    color = VaultAccentCyan,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "TOY",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    color = VaultPrimaryLight,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 3.sp
                )
            }

            // Tagline / Subtitle
            Text(
                text = "INTERACTIVE MEDIA & STUDIOS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VaultTextSecondary,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Animated Retro VU / Audio Spectrum Waveform
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(26.dp)
            ) {
                val barCount = 11
                for (i in 0 until barCount) {
                    val barHeightFraction by transition.animateFloat(
                        initialValue = 0.2f + ((i * 17) % 7) * 0.1f,
                        targetValue = 0.9f - ((i * 13) % 5) * 0.12f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 350 + (i * 45),
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bar_$i"
                    )

                    val barColor = when {
                        i == barCount / 2 -> VaultPrimaryLight
                        i % 2 == 0 -> VaultPrimary
                        else -> VaultAccentCyan
                    }

                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight(barHeightFraction.coerceIn(0.15f, 1.0f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(barColor)
                    )
                }
            }
        }

        // Bottom Tap-to-Skip Hint & Smooth Progress Indicator
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hint badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VaultSurfaceHighlight.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = VaultPrimaryLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.introTapToSkip,
                        fontSize = 12.sp,
                        color = VaultTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Thin cinematic timer bar
            LinearProgressIndicator(
                progress = { progressAnim.value },
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = VaultPrimary,
                trackColor = VaultSurfaceElevated
            )
        }
    }
}
