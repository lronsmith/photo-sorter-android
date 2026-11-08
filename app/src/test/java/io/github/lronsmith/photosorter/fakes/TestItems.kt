package io.github.lronsmith.photosorter.fakes

import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.MediaKind

/** Builds a [MediaItem] for tests; videos get `video/mp4` and a 1 s duration. */
fun testItem(
    id: Long,
    sizeBytes: Long = 1000,
    dateTaken: Long = id * 1000,
    albumId: String = "camera",
    albumName: String = "Camera",
    relativePath: String = "DCIM/Camera/",
    kind: MediaKind = MediaKind.IMAGE,
    displayName: String = "IMG_$id.jpg",
): MediaItem = MediaItem(
    id = id,
    kind = kind,
    albumId = albumId,
    albumName = albumName,
    dateTaken = dateTaken,
    sizeBytes = sizeBytes,
    relativePath = relativePath,
    displayName = displayName,
    mimeType = if (kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg",
    durationMs = if (kind == MediaKind.VIDEO) 1000L else null,
)
