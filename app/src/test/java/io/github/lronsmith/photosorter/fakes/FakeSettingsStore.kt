package io.github.lronsmith.photosorter.fakes

import io.github.lronsmith.photosorter.model.SortOrder
import io.github.lronsmith.photosorter.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SettingsStore]; tests can read or set the flows' `value` directly. */
class FakeSettingsStore(
    excluded: Set<String> = emptySet(),
    order: SortOrder = SortOrder.OLDEST_FIRST,
) : SettingsStore {
    override val excludedAlbumIds = MutableStateFlow(excluded)
    override val order = MutableStateFlow(order)

    override suspend fun setAlbumIncluded(albumId: String, included: Boolean) {
        excludedAlbumIds.update { if (included) it - albumId else it + albumId }
    }

    override suspend fun setOrder(order: SortOrder) {
        this.order.value = order
    }
}
