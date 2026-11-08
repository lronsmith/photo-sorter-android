package io.github.lronsmith.photosorter.trash

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.contentUri
import kotlin.coroutines.cancellation.CancellationException

/**
 * [Trasher] backed by `MediaStore.createTrashRequest`: `trash = true` moves to the system trash,
 * `trash = false` restores. Items are sent in chunks of [TRASH_CHUNK]; a chunk whose request
 * cannot be built, is declined, or whose launch throws fails on its own without affecting the others.
 */
class MediaStoreTrasher(
    private val sdkInt: Int,
    private val canManageMedia: () -> Boolean,
    private val requestFactory: (uris: List<Uri>, trash: Boolean) -> IntentSenderRequest,
) : Trasher {

    override fun canTrashInstantly(): Boolean = sdkInt >= Build.VERSION_CODES.S && canManageMedia()

    override suspend fun trash(items: List<MediaItem>, launcher: RequestLauncher): List<MediaItem> =
        send(items, launcher, trash = true)

    override suspend fun restore(items: List<MediaItem>, launcher: RequestLauncher): List<MediaItem> =
        send(items, launcher, trash = false)

    private suspend fun send(items: List<MediaItem>, launcher: RequestLauncher, trash: Boolean): List<MediaItem> =
        items.chunked(TRASH_CHUNK).flatMap { chunk ->
            val succeeded = try {
                launcher.launch(requestFactory(chunk.map { it.contentUri() }, trash))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (succeeded) chunk else emptyList()
        }

    companion object {
        fun create(context: Context): MediaStoreTrasher {
            val app = context.applicationContext
            return MediaStoreTrasher(
                sdkInt = Build.VERSION.SDK_INT,
                canManageMedia = {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(app)
                },
                requestFactory = { uris, trash ->
                    val sender = MediaStore.createTrashRequest(app.contentResolver, uris, trash).intentSender
                    IntentSenderRequest.Builder(sender).build()
                },
            )
        }
    }
}
