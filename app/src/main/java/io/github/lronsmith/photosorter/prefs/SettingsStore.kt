package io.github.lronsmith.photosorter.prefs

import io.github.lronsmith.photosorter.model.SortOrder
import kotlinx.coroutines.flow.Flow

/**
 * User settings. Albums are stored as an excluded set so albums that appear later are
 * included automatically. Defaults: nothing excluded, [SortOrder.OLDEST_FIRST].
 */
interface SettingsStore {
    val excludedAlbumIds: Flow<Set<String>>
    val order: Flow<SortOrder>
    suspend fun setAlbumIncluded(albumId: String, included: Boolean)
    suspend fun setOrder(order: SortOrder)
}
