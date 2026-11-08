package io.github.lronsmith.photosorter.trash

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Launches a system request (e.g. a MediaStore trash request) and returns true ⇔ `RESULT_OK`. */
fun interface RequestLauncher {
    suspend fun launch(request: IntentSenderRequest): Boolean
}

/**
 * A [RequestLauncher] bound to the current activity. Requests run one at a time; each waits for
 * its activity result. If the waiting coroutine is cancelled, the wait is abandoned and the lock
 * released, so a result that never arrives cannot block later requests. If this composable leaves
 * the composition while a request is open, that request returns false.
 */
@Composable
fun rememberRequestLauncher(): RequestLauncher {
    val pending = remember { AtomicReference<CompletableDeferred<Boolean>?>(null) }
    val activityLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
        pending.getAndSet(null)?.complete(result.resultCode == Activity.RESULT_OK)
    }
    // Leaving the composition (e.g. activity recreation) drops the activity result, so fail the
    // open request rather than strand its caller. false, not cancel: the caller's worker must survive.
    DisposableEffect(Unit) {
        onDispose { pending.getAndSet(null)?.complete(false) }
    }
    return remember(activityLauncher) {
        val mutex = Mutex()
        RequestLauncher { request ->
            mutex.withLock {
                val result = CompletableDeferred<Boolean>()
                pending.set(result)
                try {
                    withContext(Dispatchers.Main.immediate) { activityLauncher.launch(request) }
                    result.await()
                } finally {
                    pending.compareAndSet(result, null)
                    result.cancel()
                }
            }
        }
    }
}
