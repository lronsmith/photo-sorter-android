package io.github.lronsmith.photosorter.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

fun MediaItem.contentUri(): Uri {
    val collection = when (kind) {
        MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    return ContentUris.withAppendedId(collection, id)
}
