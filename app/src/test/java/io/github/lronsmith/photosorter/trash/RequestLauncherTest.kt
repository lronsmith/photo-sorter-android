package io.github.lronsmith.photosorter.trash

import android.app.PendingIntent
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequestLauncherTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After fun tearDown() = scope.cancel()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun disposingWhileRequestIsOpenReturnsFalse() {
        var show by mutableStateOf(true)
        var launcher: RequestLauncher? = null
        compose.setContent { if (show) launcher = rememberRequestLauncher() }
        compose.waitForIdle()

        val pending = PendingIntent.getActivity(
            ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE,
        )
        val call = compose.runOnIdle {
            scope.async { launcher!!.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
        }
        compose.waitForIdle()
        assertFalse("request should wait for its activity result", call.isCompleted)

        compose.runOnIdle { show = false }
        compose.waitForIdle()

        assertTrue("disposed launcher left the caller suspended", call.isCompleted)
        assertFalse(call.getCompleted())
    }
}
