package io.github.lronsmith.photosorter.model

enum class MediaKind { IMAGE, VIDEO }

enum class SortOrder { OLDEST_FIRST, NEWEST_FIRST }

/**
 * One photo or video from MediaStore. Holds no Android types so pure JVM tests can build it;
 * the content URI is derived from [id] and [kind] (see `contentUri()`).
 */
data class MediaItem(
    val id: Long,
    val kind: MediaKind,
    val albumId: String,
    val albumName: String,
    val dateTaken: Long,
    val sizeBytes: Long,
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val durationMs: Long?,
)

data class Album(
    val id: String,
    val name: String,
    val count: Int,
    val cover: MediaItem,
)
