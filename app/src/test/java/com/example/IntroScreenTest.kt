package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.RoToyIntroScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IntroScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testIntroScreenCanBeSkippedByTap() {
        var skipped = false

        composeTestRule.setContent {
            RoToyIntroScreen(
                onSkip = { skipped = true }
            )
        }

        // Verify tapping screen skips intro
        composeTestRule.onNodeWithTag("rotoy_intro_screen").performClick()
        assertTrue("Tapping intro screen should invoke onSkip", skipped)
    }

    @Test
    fun testIntroScreenSkipButton() {
        var skipped = false

        composeTestRule.setContent {
            RoToyIntroScreen(
                onSkip = { skipped = true }
            )
        }

        // Verify clicking the explicit skip button skips intro
        composeTestRule.onNodeWithTag("intro_skip_button").performClick()
        assertTrue("Clicking skip button should invoke onSkip", skipped)
    }
}
