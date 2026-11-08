package io.github.lronsmith.photosorter.ui.dupes

import io.github.lronsmith.photosorter.decisions.DecisionSource
import io.github.lronsmith.photosorter.decisions.DecisionState
import io.github.lronsmith.photosorter.fakes.FakeDecisionStore
import io.github.lronsmith.photosorter.fakes.FakeMediaSource
import io.github.lronsmith.photosorter.fakes.FakeTrasher
import io.github.lronsmith.photosorter.fakes.testItem
import io.github.lronsmith.photosorter.media.MediaSource
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.trash.RequestLauncher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicatesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    // A separate dispatcher on the same scheduler, like Dispatchers.IO vs Main in production.
    private val io = StandardTestDispatcher(dispatcher.scheduler)
    private val media = FakeMediaSource()
    private val decisions = FakeDecisionStore()
    private val trasher = FakeTrasher()
    private val launcher = RequestLauncher { true }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = DuplicatesViewModel(media, decisions, trasher, ioDispatcher = io)

    /** Adds an item whose content is [bytes] (size = bytes.size). */
    private fun add(id: Long, bytes: ByteArray, relativePath: String = "DCIM/Camera/") {
        media.items += testItem(id, sizeBytes = bytes.size.toLong(), relativePath = relativePath)
        media.contents[id] = bytes
    }

    private fun TestScope.scanned(): Pair<DuplicatesViewModel, DuplicatesUiState.Results> {
        val vm = viewModel()
        vm.scan()
        advanceUntilIdle()
        return vm to vm.state.value as DuplicatesUiState.Results
    }

    @Test fun scanProducesResults() = runTest(dispatcher) {
        add(1, byteArrayOf(1, 2, 3))
        add(2, byteArrayOf(1, 2, 3))
        add(3, byteArrayOf(9, 9, 9))

        val (_, results) = scanned()

        assertEquals(1, results.groups.size)
        assertEquals(listOf(1L, 2L), results.groups[0].copies.map { it.id })
        assertEquals(1L, results.groups[0].keeperId)
        assertEquals(1, results.copiesToTrash)
        assertEquals(3L, results.bytesFreed)
        assertEquals(0, results.unreadableCount)
    }

    @Test fun progressReportedWhileScanning() = runTest(dispatcher) {
        add(1, ByteArray(200_000) { 1 })
        add(2, ByteArray(200_000) { 1 })
        val vm = viewModel()
        val states = mutableListOf<DuplicatesUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(states) }

        vm.scan()
        advanceUntilIdle()

        val firstProgress = states.indexOfFirst { it is DuplicatesUiState.Scanning && it.progress != null }
        val firstResults = states.indexOfFirst { it is DuplicatesUiState.Results }
        assertTrue("states: $states", firstProgress >= 0)
        assertTrue("states: $states", firstProgress < firstResults)
    }

    @Test fun cancelReturnsToIdle() = runTest(dispatcher) {
        add(1, byteArrayOf(1))
        add(2, byteArrayOf(1))
        val gate = CompletableDeferred<Unit>()
        val slow = object : MediaSource by media {
            override suspend fun openStream(item: MediaItem): InputStream? {
                gate.await()
                return media.openStream(item)
            }
        }
        val vm = DuplicatesViewModel(slow, decisions, trasher, ioDispatcher = io)

        vm.scan()
        advanceUntilIdle()
        assertTrue(vm.state.value is DuplicatesUiState.Scanning)
        vm.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(DuplicatesUiState.Idle, vm.state.value)
    }

    @Test fun scanIsIgnoredWhileScanning() = runTest(dispatcher) {
        add(1, byteArrayOf(1))
        add(2, byteArrayOf(1))
        var listings = 0
        val counting = object : MediaSource by media {
            override suspend fun allItems(): List<MediaItem> = media.allItems().also { listings++ }
        }
        val vm = DuplicatesViewModel(counting, decisions, trasher, ioDispatcher = io)

        vm.scan()
        vm.scan()
        advanceUntilIdle()

        assertEquals(1, listings)
        assertEquals(1, (vm.state.value as DuplicatesUiState.Results).groups.size)
    }

    @Test fun setKeeperChangesKeeper() = runTest(dispatcher) {
        add(1, byteArrayOf(1, 2))
        add(2, byteArrayOf(1, 2))
        val (vm, results) = scanned()

        vm.setKeeper(results.groups[0].hash, 2L)

        val group = (vm.state.value as DuplicatesUiState.Results).groups.single()
        assertEquals(2L, group.keeperId)
    }

    @Test fun trashAllTrashesNonKeepersAndRecords() = runTest(dispatcher) {
        add(1, byteArrayOf(1, 1))
        add(2, byteArrayOf(1, 1))
        add(3, byteArrayOf(2, 2, 2))
        add(4, byteArrayOf(2, 2, 2))
        add(5, byteArrayOf(2, 2, 2))
        val (vm, results) = scanned()
        assertEquals(listOf(1L, 3L), results.groups.map { it.keeperId }.sorted())

        vm.trashAll(launcher)
        advanceUntilIdle()

        assertEquals(listOf(listOf(2L, 4L, 5L)), trasher.trashCalls.map { it.sorted() })
        assertEquals(setOf(2L, 4L, 5L), decisions.rows.keys)
        decisions.rows.values.forEach {
            assertEquals(DecisionState.TRASHED, it.state)
            assertEquals(DecisionSource.DUPLICATE, it.source)
        }
        val after = vm.state.value as DuplicatesUiState.Results
        assertTrue(after.groups.isEmpty())
        assertEquals(false, after.trashing)
        assertNull(after.message)
    }

    @Test fun partialTrashKeepsUnfinishedGroups() = runTest(dispatcher) {
        add(1, byteArrayOf(1, 1))
        add(2, byteArrayOf(1, 1))
        add(3, byteArrayOf(2, 2, 2))
        add(4, byteArrayOf(2, 2, 2))
        add(5, byteArrayOf(2, 2, 2))
        trasher.trashResult = { it.filter { i -> i.id != 5L } }
        val (vm, _) = scanned()

        vm.trashAll(launcher)
        advanceUntilIdle()

        val after = vm.state.value as DuplicatesUiState.Results
        assertEquals(listOf(listOf(3L, 5L)), after.groups.map { g -> g.copies.map { it.id } })
        assertEquals(3L, after.groups.single().keeperId)
        assertEquals(1, after.copiesToTrash)
        assertEquals("Some copies weren't trashed.", after.message)
        assertEquals(setOf(2L, 4L), decisions.rows.keys)

        vm.onMessageShown()
        assertNull((vm.state.value as DuplicatesUiState.Results).message)
    }

    @Test fun unreadableCountSurfaced() = runTest(dispatcher) {
        add(1, byteArrayOf(1, 2))
        add(2, byteArrayOf(1, 2))
        add(3, byteArrayOf(1, 2))
        media.contents.remove(2L)
        media.contents.remove(3L)

        val (_, results) = scanned()

        assertEquals(2, results.unreadableCount)
        assertTrue(results.groups.isEmpty())
    }

    @Test fun noDuplicates() = runTest(dispatcher) {
        add(1, byteArrayOf(1))
        add(2, byteArrayOf(2))
        add(3, byteArrayOf(3, 3))

        val (_, results) = scanned()

        assertTrue(results.groups.isEmpty())
        assertEquals(0, results.copiesToTrash)
        assertEquals(0L, results.bytesFreed)
    }
}
