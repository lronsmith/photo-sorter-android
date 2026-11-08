package io.github.lronsmith.photosorter.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lronsmith.photosorter.AppDeps
import io.github.lronsmith.photosorter.fakes.FakeDecisionStore
import io.github.lronsmith.photosorter.fakes.FakeMediaSource
import io.github.lronsmith.photosorter.fakes.FakeSettingsStore
import io.github.lronsmith.photosorter.fakes.FakeTrasher
import io.github.lronsmith.photosorter.fakes.testItem
import io.github.lronsmith.photosorter.media.AccessLevel
import io.github.lronsmith.photosorter.ui.theme.PhotoSorterTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test: the app starts on the album grid and reaches every screen. */
@RunWith(AndroidJUnit4::class)
class PhotoSorterAppTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var fakeDeps: FakeDeps

    @Before fun setUp() {
        fakeDeps = FakeDeps(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        rule.setContent { PhotoSorterTheme { PhotoSorterApp(fakeDeps) } }
    }

    @After fun tearDown() {
        fakeDeps.appScope.cancel()
    }

    @Test fun startShowsAlbumsAndNavigates() {
        rule.onNodeWithText("Choose albums").assertIsDisplayed()

        rule.onNodeWithText("Find duplicates").performClick()
        rule.onNodeWithText("Duplicates").assertIsDisplayed()

        Espresso.pressBack()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Trashed by this app").assertIsDisplayed()

        rule.onNodeWithText("Trashed by this app").performClick()
        rule.onNodeWithText("Nothing in the trash").assertIsDisplayed()
    }

    @Test fun startOpensSwipe() {
        rule.onNodeWithText("Start ›").performClick()
        rule.onNodeWithTag("swipe-card").assertExists()
    }

    private class FakeDeps(override val appScope: CoroutineScope) : AppDeps {
        override val media = FakeMediaSource((1L..3L).map { testItem(it) })
        override val decisions = FakeDecisionStore()
        override val settings = FakeSettingsStore()
        override val trasher = FakeTrasher()
        override fun accessLevel() = AccessLevel.FULL
        override val sdkInt = 37
        override val versionLabel = "1.0.0 (build 1)"
    }
}
