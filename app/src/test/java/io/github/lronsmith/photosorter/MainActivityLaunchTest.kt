package io.github.lronsmith.photosorter

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real app (manifest Application, production container) launches to the start screen. */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun launchesToStartScreenWithProductionContainer() {
        assertTrue(rule.activity.application is PhotoSorterApplication)
        rule.onNodeWithText("Choose albums").assertIsDisplayed()
        rule.onNodeWithText("Find duplicates").assertIsDisplayed()
        // No media permission granted under Robolectric: the start screen asks for access.
        rule.onNodeWithText("Grant access").assertIsDisplayed()
    }
}
