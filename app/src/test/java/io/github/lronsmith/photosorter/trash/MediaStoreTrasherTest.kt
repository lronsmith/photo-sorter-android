package io.github.lronsmith.photosorter.trash

import android.app.PendingIntent
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.lronsmith.photosorter.fakes.testItem
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreTrasherTest {
    private val factoryCalls = mutableListOf<Pair<Int, Boolean>>()
    private val ok = RequestLauncher { true }

    private fun trasher(sdk: Int = 31, manage: Boolean = true) = MediaStoreTrasher(
        sdkInt = sdk,
        canManageMedia = { manage },
        requestFactory = { uris, trash ->
            factoryCalls += uris.size to trash
            val pending = PendingIntent.getActivity(
                ApplicationProvider.getApplicationContext(),
                0,
                Intent(),
                PendingIntent.FLAG_IMMUTABLE,
            )
            IntentSenderRequest.Builder(pending.intentSender).build()
        },
    )

    @Test fun instantOnlyWithPermissionOnApi31Plus() {
        assertFalse(trasher(sdk = 30, manage = true).canTrashInstantly())
        assertFalse(trasher(31, false).canTrashInstantly())
        assertTrue(trasher(31, true).canTrashInstantly())
    }

    @Test fun chunksAt500() = runTest {
        trasher().trash((1L..1200).map { testItem(it) }, ok)
        assertEquals(listOf(500 to true, 500 to true, 200 to true), factoryCalls)
    }

    @Test fun trashReturnsOnlyItemsFromSuccessfulChunks() = runTest {
        val results = ArrayDeque(listOf(true, false, true))
        val done = trasher().trash((1L..1200).map { testItem(it) }) { results.removeFirst() }
        assertEquals((1L..500) + (1001L..1200), done.map { it.id })
    }

    @Test fun restoreUsesUntrash() = runTest {
        trasher().restore(listOf(testItem(1)), ok)
        assertEquals(listOf(1 to false), factoryCalls)
    }

    @Test fun emptyInputLaunchesNothing() = runTest {
        assertTrue(trasher().trash(emptyList(), ok).isEmpty())
        assertTrue(factoryCalls.isEmpty())
    }

    @Test fun launcherExceptionFailsThatChunk() = runTest {
        assertTrue(trasher().trash(listOf(testItem(1))) { throw IllegalStateException() }.isEmpty())
    }

    @Test fun launcherCancellationPropagates() = runTest {
        assertFailsWith<CancellationException> {
            trasher().trash(listOf(testItem(1))) { throw CancellationException("cancelled") }
        }
    }

    @Test fun requestFactoryExceptionFailsOnlyThatChunk() = runTest {
        var built = 0
        val trasher = MediaStoreTrasher(31, { true }) { _, _ ->
            if (built++ == 0) throw IllegalArgumentException()
            IntentSenderRequest.Builder(
                PendingIntent.getActivity(
                    ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE,
                ).intentSender,
            ).build()
        }
        assertEquals((501L..600).toList(), trasher.trash((1L..600).map { testItem(it) }, ok).map { it.id })
    }
}
