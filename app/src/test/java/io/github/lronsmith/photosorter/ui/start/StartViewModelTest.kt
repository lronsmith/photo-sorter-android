package io.github.lronsmith.photosorter.ui.start

import io.github.lronsmith.photosorter.decisions.DecisionSource.SWIPE
import io.github.lronsmith.photosorter.decisions.DecisionState.KEPT
import io.github.lronsmith.photosorter.decisions.DecisionState.PENDING_TRASH
import io.github.lronsmith.photosorter.decisions.DecisionState.TRASHED
import io.github.lronsmith.photosorter.fakes.FakeDecisionStore
import io.github.lronsmith.photosorter.fakes.FakeMediaSource
import io.github.lronsmith.photosorter.fakes.FakeSettingsStore
import io.github.lronsmith.photosorter.fakes.FakeTrasher
import io.github.lronsmith.photosorter.fakes.testItem
import io.github.lronsmith.photosorter.media.AccessLevel
import io.github.lronsmith.photosorter.media.MediaSource
import io.github.lronsmith.photosorter.media.TrashedItem
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.SortOrder
import io.github.lronsmith.photosorter.trash.RequestLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartViewModelTest {
    private val media = CountingMediaSource()
    private val decisions = FakeDecisionStore()
    private val settings = FakeSettingsStore()
    private val trasher = FakeTrasher()
    private var access = AccessLevel.FULL
    private val ok = RequestLauncher { true }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.loaded(): StartViewModel {
        val vm = StartViewModel(media, decisions, settings, trasher) { access }
        vm.refresh()
        advanceUntilIdle()
        return vm
    }

    /** camera 1, 2, 3 + wa 4. */
    private fun cameraAndWa() {
        media.items = listOf(testItem(1), testItem(2), testItem(3), testItem(4, albumId = "wa", albumName = "WhatsApp"))
    }

    @Test fun noAccessLoadsNothing() = runTest {
        access = AccessLevel.NONE
        cameraAndWa()
        val state = loaded().state.value
        assertEquals(AccessLevel.NONE, state.access)
        assertFalse(state.loading)
        assertTrue(state.albums.isEmpty())
        assertEquals(0, media.allItemsCalls)
    }

    @Test fun albumsWithIncludedFlags() = runTest {
        cameraAndWa()
        settings.excludedAlbumIds.value = setOf("wa")
        val state = loaded().state.value
        assertEquals(listOf("camera" to true, "wa" to false), state.albums.map { it.album.id to it.included })
        assertEquals(listOf(3, 1), state.albums.map { it.album.count })
        assertEquals(1, state.selectedAlbums)
        assertFalse(state.loading)
    }

    @Test fun queueCountExcludesDecidedAndExcludedAlbums() = runTest {
        cameraAndWa()
        decisions.set(listOf(2), KEPT, SWIPE)
        settings.excludedAlbumIds.value = setOf("wa")
        val state = loaded().state.value
        assertEquals(2, state.queueCount)
        assertEquals(3, state.albums.first { it.album.id == "camera" }.album.count) // decided items still counted
    }

    @Test fun toggleAlbumPersistsAndRecounts() = runTest {
        cameraAndWa()
        decisions.set(listOf(2), KEPT, SWIPE)
        settings.excludedAlbumIds.value = setOf("wa")
        val vm = loaded()
        vm.toggleAlbum("wa")
        advanceUntilIdle()
        assertEquals(emptySet<String>(), settings.excludedAlbumIds.value)
        assertEquals(3, vm.state.value.queueCount)
        assertEquals(2, vm.state.value.selectedAlbums)
        assertTrue(vm.state.value.albums.all { it.included })
    }

    @Test fun cannotStartWithNothingSelectedOrEmptyQueue() = runTest {
        cameraAndWa()
        settings.excludedAlbumIds.value = setOf("camera", "wa")
        val excludedAll = loaded()
        assertEquals(0, excludedAll.state.value.selectedAlbums)
        assertFalse(excludedAll.state.value.canStart)

        settings.excludedAlbumIds.value = emptySet()
        decisions.set(listOf(1, 2, 3, 4), KEPT, SWIPE)
        val allDecided = loaded()
        assertEquals(0, allDecided.state.value.queueCount)
        assertFalse(allDecided.state.value.canStart)

        decisions.clear(listOf(4))
        allDecided.refresh()
        advanceUntilIdle()
        assertTrue(allDecided.state.value.canStart)
    }

    @Test fun setOrderPersists() = runTest {
        cameraAndWa()
        val vm = loaded()
        vm.setOrder(SortOrder.NEWEST_FIRST)
        advanceUntilIdle()
        assertEquals(SortOrder.NEWEST_FIRST, settings.order.value)
        assertEquals(SortOrder.NEWEST_FIRST, vm.state.value.order)
    }

    @Test fun refreshPrunesTrashedRows() = runTest {
        cameraAndWa()
        decisions.set(listOf(1, 2), TRASHED, SWIPE)
        media.items = media.items.filter { it.id !in setOf(1L, 2L) }
        media.trashed[2] = TrashedItem(testItem(2), expiresAt = 0)
        loaded()
        assertEquals(setOf(2L), decisions.decidedIds())
    }

    @Test fun partialAccessDoesNotPrune() = runTest {
        access = AccessLevel.PARTIAL
        cameraAndWa()
        decisions.set(listOf(1, 2), TRASHED, SWIPE)
        media.items = media.items.filter { it.id !in setOf(1L, 2L) }
        media.trashed[2] = TrashedItem(testItem(2), expiresAt = 0)
        val state = loaded().state.value
        assertEquals(setOf(1L, 2L), decisions.decidedIds())
        assertTrue(state.albums.isNotEmpty()) // still loads
    }

    @Test fun pendingBannerAndTrashNow() = runTest {
        cameraAndWa()
        decisions.set(listOf(1, 2), PENDING_TRASH, SWIPE)
        val vm = loaded()
        assertEquals(2, vm.state.value.pendingCount)
        vm.onTrashPending(ok)
        advanceUntilIdle()
        assertEquals(listOf(listOf(1L, 2L)), trasher.trashCalls)
        assertEquals(listOf(1L, 2L), decisions.idsIn(TRASHED))
        assertEquals(0, vm.state.value.pendingCount)
        assertNull(vm.state.value.message)
    }

    @Test fun partialTrashKeepsRestPendingWithMessage() = runTest {
        cameraAndWa()
        decisions.set(listOf(1, 2), PENDING_TRASH, SWIPE)
        trasher.trashResult = { items -> items.take(1) }
        val vm = loaded()
        vm.onTrashPending(ok)
        advanceUntilIdle()
        assertEquals(listOf(1L), decisions.idsIn(TRASHED))
        assertEquals(listOf(2L), decisions.idsIn(PENDING_TRASH))
        assertEquals(1, vm.state.value.pendingCount)
        assertEquals("Some items weren't trashed. They're still waiting.", vm.state.value.message)
        vm.onMessageShown()
        assertNull(vm.state.value.message)
    }

    @Test fun partialAccessStillLoads() = runTest {
        access = AccessLevel.PARTIAL
        cameraAndWa()
        val state = loaded().state.value
        assertEquals(AccessLevel.PARTIAL, state.access)
        assertTrue(state.albums.isNotEmpty())
        assertEquals(4, state.queueCount)
    }

    /** [FakeMediaSource] that counts [allItems] calls. */
    private class CountingMediaSource(private val inner: FakeMediaSource = FakeMediaSource()) :
        MediaSource by inner {
        var allItemsCalls = 0
        var items: List<MediaItem> by inner::items
        val trashed get() = inner.trashed

        override suspend fun allItems(): List<MediaItem> {
            allItemsCalls++
            return inner.allItems()
        }
    }
}
