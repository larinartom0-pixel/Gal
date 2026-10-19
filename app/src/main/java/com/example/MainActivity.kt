package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SettingsManager
import com.example.ui.GalleryScreen
import com.example.ui.GalleryViewModel
import com.example.ui.RoToyIntroScreen
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  companion object {
    // Tracks if intro has already been displayed during current app process/session
    var hasShownIntroInSession: Boolean = false
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Critical fix: Prevent duplicate activity/task creation when user clicks launcher icon while app is running
    if (!isTaskRoot
        && intent.hasCategory(android.content.Intent.CATEGORY_LAUNCHER)
        && intent.action != null
        && intent.action == android.content.Intent.ACTION_MAIN
    ) {
        finish()
        return
    }

    enableEdgeToEdge()
    setContent {
      val galleryViewModel: GalleryViewModel = viewModel()
      val appLanguage by galleryViewModel.appLanguage.collectAsStateWithLifecycle()
      val playIntroOnLaunch by galleryViewModel.playIntroOnLaunch.collectAsStateWithLifecycle()

      val effectiveLanguage = remember(appLanguage) {
        SettingsManager.resolveEffectiveLanguage(appLanguage)
      }
      val appStrings = remember(effectiveLanguage) {
        stringsForLanguage(effectiveLanguage)
      }

      CompositionLocalProvider(LocalAppStrings provides appStrings) {
        MyApplicationTheme {
          Surface(modifier = Modifier.fillMaxSize()) {
            // Only show intro if enabled in settings AND not already shown during this session
            val initialIntroState = remember {
              val shouldShow = playIntroOnLaunch && !hasShownIntroInSession
              if (shouldShow) {
                hasShownIntroInSession = true
              }
              shouldShow
            }
            var showIntro by rememberSaveable { mutableStateOf(initialIntroState) }

            AnimatedContent(
              targetState = showIntro,
              transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
              },
              label = "intro_transition",
              modifier = Modifier.fillMaxSize()
            ) { isIntroVisible ->
              if (isIntroVisible) {
                RoToyIntroScreen(
                  onSkip = {
                    hasShownIntroInSession = true
                    showIntro = false
                  }
                )
              } else {
                GalleryScreen(
                  viewModel = galleryViewModel,
                  modifier = Modifier.fillMaxSize(),
                  onPlayIntro = { showIntro = true }
                )
              }
            }
          }
        }
      }
    }
  }
}

