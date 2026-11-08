package io.github.lronsmith.photosorter.dupes

import io.github.lronsmith.photosorter.model.MediaItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Byte-identical copies (same size and SHA-256), in ascending id order. */
data class DuplicateGroup(val hash: String, val copies: List<MediaItem>, val keeperId: Long) {
    val bytesFreed: Long get() = copies.first().sizeBytes * (copies.size - 1)
}

/** Files and bytes hashed so far; totals cover only same-size candidates. */
data class ScanProgress(val filesDone: Int, val filesTotal: Int, val bytesDone: Long, val bytesTotal: Long)

/** [groups] sorted by [DuplicateGroup.bytesFreed] desc, then by smallest copy id. */
data class ScanResult(val groups: List<DuplicateGroup>, val unreadableCount: Int)

/**
 * Finds exact duplicates. Only non-empty files that share their size with another file are
 * opened; each is streamed through SHA-256, never loaded whole. Cancellable between buffers.
 * Runs on the caller's dispatcher.
 */
object DuplicateFinder {
    private const val BUFFER_SIZE = 64 * 1024
    private const val CAMERA_PATH = "DCIM/Camera/"

    private val keeperOrder = compareBy<MediaItem>(
        { !it.relativePath.startsWith(CAMERA_PATH) },
        { it.dateTaken },
        { it.id },
    )

    private val groupOrder = compareByDescending<DuplicateGroup> { it.bytesFreed }
        .thenBy { it.copies.first().id }

    suspend fun scan(
        items: List<MediaItem>,
        openStream: suspend (MediaItem) -> InputStream?,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanResult {
        // Sorted by id so every group's copies come out in ascending id order.
        val candidates = items.filter { it.sizeBytes > 0 }
            .groupBy { it.sizeBytes }.values
            .filter { it.size > 1 }
            .flatten()
            .sortedBy { it.id }
        val filesTotal = candidates.size
        val bytesTotal = candidates.sumOf { it.sizeBytes }
        var filesDone = 0
        var bytesDone = 0L
        var unreadableCount = 0
        val bySizeAndHash = LinkedHashMap<Pair<Long, String>, MutableList<MediaItem>>()
        val buffer = ByteArray(BUFFER_SIZE)

        onProgress(ScanProgress(filesDone, filesTotal, bytesDone, bytesTotal))
        for (item in candidates) {
            currentCoroutineContext().ensureActive()
            val hash = sha256(item, openStream, buffer) { readInFile ->
                onProgress(ScanProgress(filesDone, filesTotal, bytesDone + readInFile, bytesTotal))
            }
            if (hash == null) {
                unreadableCount++
            } else {
                bySizeAndHash.getOrPut(item.sizeBytes to hash) { mutableListOf() } += item
            }
            filesDone++
            bytesDone += item.sizeBytes
            onProgress(ScanProgress(filesDone, filesTotal, bytesDone, bytesTotal))
        }

        val groups = bySizeAndHash
            .filterValues { it.size > 1 }
            .map { (key, copies) -> DuplicateGroup(key.second, copies, pickKeeper(copies).id) }
            .sortedWith(groupOrder)
        return ScanResult(groups, unreadableCount)
    }

    /** Camera-folder copies first, then the oldest `dateTaken`, then the smallest id. */
    fun pickKeeper(copies: List<MediaItem>): MediaItem = copies.minWith(keeperOrder)

    /**
     * Lower-case hex SHA-256 of [item]'s content, or `null` if it can't be opened or read.
     * [onBytesRead] gets the bytes read so far in this file (capped at its size).
     */
    private suspend fun sha256(
        item: MediaItem,
        openStream: suspend (MediaItem) -> InputStream?,
        buffer: ByteArray,
        onBytesRead: (Long) -> Unit,
    ): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val stream = openStream(item) ?: return null
            stream.use { input ->
                var readInFile = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    readInFile += n
                    onBytesRead(minOf(readInFile, item.sizeBytes))
                    currentCoroutineContext().ensureActive()
                }
            }
        } catch (_: IOException) {
            return null
        }
        return digest.digest().toHexString()
    }
}
