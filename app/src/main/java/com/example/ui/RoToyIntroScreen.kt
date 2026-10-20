package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Clean, lightweight Intro screen showing what the app is:
 * - App Name: "gal" (Private Gallery & Video Vault)
 * - License: GNU General Public License v3 (GPLv3)
 * - Without any company names or heavy animations
 * - Fast, smooth auto-skip in ~2 seconds or instant tap-to-skip
 */
@Composable
fun RoToyIntroScreen(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    // Back button immediately skips intro
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

    val entranceAnim = remember { Animatable(0f) }
    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Fast, smooth entrance
        entranceAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
        // Clean linear progress bar over 2.0 seconds
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
        )
        delay(100)
        onSkip()
    }

    // Full screen tap to skip immediately
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        VaultBackground,
                        Color(0xFF13151A),
                        VaultBackground
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
        // Top skip button
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = VaultSurfaceElevated.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier
                    .clip(RoundedCornerShape(0.dp))
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = strings.introSkipDesc,
                        tint = VaultPrimaryLight,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Center Content: App Name, Purpose, License
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .alpha(entranceAnim.value)
        ) {
            // Elegant App Shield Icon
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = VaultPrimary.copy(alpha = 0.15f),
                border = BorderStroke(1.5.dp, VaultPrimaryLight.copy(alpha = 0.6f)),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = VaultPrimaryLight,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Program Name: "gal"
            Text(
                text = strings.introAppTitle,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = VaultTextPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // What the program is: Subtitle
            Text(
                text = strings.introTagline,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VaultPrimaryLight,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = strings.introAppDescription,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = VaultTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth(0.9f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // LICENSE CARD: GNU General Public License v3
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = VaultSurfaceElevated.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, VaultBorderSubtle),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(0.dp))
                            .background(VaultAccentCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = VaultAccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.introLicenseBadge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultTextPrimary
                        )
                        Text(
                            text = strings.introLicenseDetails,
                            fontSize = 10.sp,
                            color = VaultTextSecondary,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        // Bottom Tap-to-Skip Hint & Progress Indicator
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = VaultSurfaceHighlight.copy(alpha = 0.35f),
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
                        tint = VaultTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = strings.introTapToSkip,
                        fontSize = 11.sp,
                        color = VaultTextMuted,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { progressAnim.value },
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(0.dp)),
                color = VaultPrimary,
                trackColor = VaultSurfaceElevated
            )
        }
    }
}
