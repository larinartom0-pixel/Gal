package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = VaultPrimary,
    onPrimary = VaultOnPrimary,
    primaryContainer = VaultPrimaryDark,
    onPrimaryContainer = VaultPrimaryLight,
    secondary = VaultSurfaceElevated,
    onSecondary = VaultTextPrimary,
    secondaryContainer = VaultSurfaceHighlight,
    onSecondaryContainer = VaultTextPrimary,
    tertiary = VaultAccentCyan,
    onTertiary = VaultOnPrimary,
    background = VaultBackground,
    onBackground = VaultTextPrimary,
    surface = VaultSurface,
    onSurface = VaultTextPrimary,
    surfaceVariant = VaultSurfaceElevated,
    onSurfaceVariant = VaultTextSecondary,
    outline = VaultBorder,
    outlineVariant = VaultBorderSubtle,
    error = VaultError,
    onError = VaultOnError,
    errorContainer = VaultErrorContainer,
    onErrorContainer = VaultError
  )

private val LightColorScheme = DarkColorScheme // Default to dark for stealth/secure vault vibes

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to true for secure dark vibes
  dynamicColor: Boolean = false, // Disable default dynamic overlays to lock-in the Sophisticated Dark look
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = RectangularShapes,
    content = content
  )
}
