package io.github.lronsmith.photosorter.dupes

import io.github.lronsmith.photosorter.fakes.testItem
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class DuplicateFinderTest {
    /** Ids of items whose stream was opened / closed, in any order. */
    private val opened = mutableSetOf<Long>()
    private val closed = mutableSetOf<Long>()

    /** Number of read calls per item id. */
    private val reads = mutableMapOf<Long, Int>()

    /** Called by the `onFirstRead` hook of the cancellation tests; set once the job exists. */
    private var cancelHook: () -> Unit = {}

    /** 1 MiB of identical content, so a scan spans many 64 KiB buffers. */
    private val big = "x".repeat(1 shl 20)

    @Test fun groupsIdenticalContentOnly() = runTest { // ids 1,2 same bytes; 3 same size, different bytes; 4 unique size
        val r = scan(1 to "aaaa", 2 to "aaaa", 3 to "bbbb", 4 to "ccccc")
        assertEquals(listOf(listOf(1L, 2L)), r.groups.map { g -> g.copies.map { it.id } })
        assertEquals(0, r.unreadableCount)
    }

    @Test fun groupHasLowerCaseSha256AndKeeper() = runTest {
        val g = scan(1 to "aaaa", 2 to "aaaa").groups.single()
        assertEquals("61be55a8e2f6b4e172338bddf184d6dbee29c98853e0a0485ecee7f27b9af0b4", g.hash)
        assertEquals(1L, g.keeperId)
    }

    @Test fun onlySameSizeCandidatesAreOpened() = runTest {
        scan(1 to "aa", 2 to "aa", 3 to "xyz")
        assertEquals(setOf(1L, 2L), opened)
    }

    @Test fun zeroByteFilesIgnored() = runTest {
        assertTrue(scan(1 to "", 2 to "").groups.isEmpty())
        assertTrue(opened.isEmpty())
    }

    @Test fun keeperPrefersCameraThenOldestThenId() {
        val a = testItem(1, dateTaken = 50, relativePath = "Download/")
        val b = testItem(2, dateTaken = 90)
        val c = testItem(3, dateTaken = 90)
        assertEquals(2L, DuplicateFinder.pickKeeper(listOf(a, b, c)).id)
        assertEquals(1L, DuplicateFinder.pickKeeper(listOf(a, testItem(4, dateTaken = 60, relativePath = "Pictures/"))).id)
    }

    @Test fun unreadableFilesCountedAndExcluded() = runTest { // id 2 -> null stream, id 3 -> throws IOException mid-read
        val r = scan(1 to "aa", 2 to "aa", 3 to "aa", unreadable = setOf(2L), throwing = setOf(3L))
        assertTrue(r.groups.isEmpty())
        assertEquals(2, r.unreadableCount)
    }

    @Test fun groupsSortedByBytesFreed() = runTest {
        val r = scan(1 to "a", 2 to "a", 3 to "bbbbb", 4 to "bbbbb", 5 to "bbbbb")
        assertEquals(listOf(10L, 1L), r.groups.map { it.bytesFreed })
    }

    @Test fun tiesSortedBySmallestCopyIdAndCopiesAscending() = runTest {
        val r = scan(5 to "aa", 4 to "bb", 2 to "aa", 3 to "bb")
        assertEquals(listOf(listOf(2L, 5L), listOf(3L, 4L)), r.groups.map { g -> g.copies.map { it.id } })
    }

    @Test fun progressReachesTotals() = runTest {
        val p = mutableListOf<ScanProgress>()
        scan(1 to "aa", 2 to "aa", 3 to "xyz", progress = p::add)
        assertEquals(ScanProgress(2, 2, 4, 4), p.last())
        assertTrue(p.all { it.filesTotal == 2 && it.bytesTotal == 4L && it.bytesDone <= 4L })
    }

    @Test fun progressReachesTotalsDespiteUnreadable() = runTest {
        val p = mutableListOf<ScanProgress>()
        scan(1 to "aa", 2 to "aa", 3 to "aa", unreadable = setOf(2L), throwing = setOf(3L), progress = p::add)
        assertEquals(ScanProgress(3, 3, 6, 6), p.last())
    }

    @Test fun closesEveryStreamIncludingOnErrorAndCancel() = runTest {
        scan(1 to "aa", 2 to "aa", 3 to "aa", throwing = setOf(3L))
        assertEquals(opened, closed)
        opened.clear(); closed.clear()

        val job = cancelDuringFirstReadOfId10()
        assertTrue(job.isCancelled)
        assertEquals(setOf(10L), opened)
        assertEquals(opened, closed)
    }

    @Test fun cancellationStopsScan() = runTest { // same setup: after cancel during id 10's first read, id 11 is never opened
        cancelDuringFirstReadOfId10()
        assertFalse(11L in opened)
        assertEquals(1, reads[10L]) // stopped between buffers, not after reading all 1 MiB
    }

    /** Ids 10, 11: 1 MiB each, identical; the stream for id 10 cancels the scan job on its first read. */
    private suspend fun TestScope.cancelDuringFirstReadOfId10(): Job {
        val job = launch(start = CoroutineStart.LAZY) {
            scan(10 to big, 11 to big, onFirstRead = mapOf(10L to { cancelHook() }))
        }
        cancelHook = { job.cancel() }
        job.join()
        return job
    }

    /** Scans items whose size is their content length; ids in [unreadable] open as `null`. */
    private suspend fun scan(
        vararg files: Pair<Int, String>,
        unreadable: Set<Long> = emptySet(),
        throwing: Set<Long> = emptySet(),
        onFirstRead: Map<Long, () -> Unit> = emptyMap(),
        progress: (ScanProgress) -> Unit = {},
    ): ScanResult {
        val bytes = files.associate { (id, content) -> id.toLong() to content.toByteArray() }
        val items = bytes.map { (id, b) -> testItem(id, sizeBytes = b.size.toLong()) }
        return DuplicateFinder.scan(
            items,
            openStream = { item ->
                if (item.id in unreadable) null
                else RecordingStream(item.id, bytes.getValue(item.id), item.id in throwing, onFirstRead[item.id])
                    .also { opened += item.id }
            },
            onProgress = progress,
        )
    }

    /**
     * Records its close in [closed]; runs [onFirstRead] on the first read; when [throwing],
     * every read after the first throws [IOException] (a failure mid-file).
     */
    private inner class RecordingStream(
        private val id: Long,
        content: ByteArray,
        private val throwing: Boolean,
        private val onFirstRead: (() -> Unit)?,
    ) : InputStream() {
        private val inner = ByteArrayInputStream(content)
        private fun beforeRead() {
            val n = reads[id] ?: 0
            if (n == 0) onFirstRead?.invoke()
            if (throwing && n > 0) throw IOException("read failed for $id")
            reads[id] = n + 1
        }

        override fun read(): Int { beforeRead(); return inner.read() }
        override fun read(b: ByteArray, off: Int, len: Int): Int { beforeRead(); return inner.read(b, off, len) }
        override fun close() { closed += id; inner.close() }
    }
}
