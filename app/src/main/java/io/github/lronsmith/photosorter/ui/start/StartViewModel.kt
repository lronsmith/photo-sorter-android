package io.github.lronsmith.photosorter.ui.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lronsmith.photosorter.decisions.DecisionSource
import io.github.lronsmith.photosorter.decisions.DecisionState
import io.github.lronsmith.photosorter.decisions.DecisionStore
import io.github.lronsmith.photosorter.media.AccessLevel
import io.github.lronsmith.photosorter.media.Library
import io.github.lronsmith.photosorter.media.MediaSource
import io.github.lronsmith.photosorter.model.Album
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.SortOrder
import io.github.lronsmith.photosorter.prefs.SettingsStore
import io.github.lronsmith.photosorter.trash.RequestLauncher
import io.github.lronsmith.photosorter.trash.Trasher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AlbumRow(val album: Album, val included: Boolean)

data class StartUiState(
    val loading: Boolean = true,
    val access: AccessLevel = AccessLevel.NONE,
    val albums: List<AlbumRow> = emptyList(),
    val selectedAlbums: Int = 0,
    val queueCount: Int = 0,
    val order: SortOrder = SortOrder.OLDEST_FIRST,
    val pendingCount: Int = 0,
    val message: String? = null,
) {
    val canStart: Boolean get() = selectedAlbums > 0 && queueCount > 0
}

private const val PENDING_NOT_TRASHED = "Some items weren't trashed. They're still waiting."

/**
 * Album selection, order and pending-trash banner for the Start screen. [refresh] (called on every
 * resume) re-checks access, prunes stale TRASHED rows, then reloads the library.
 */
class StartViewModel(
    private val media: MediaSource,
    private val decisions: DecisionStore,
    private val settings: SettingsStore,
    private val trasher: Trasher,
    private val accessLevel: () -> AccessLevel,
) : ViewModel() {
    private val _state = MutableStateFlow(StartUiState())
    val state: StateFlow<StartUiState> = _state.asStateFlow()

    // The loaded library and the inputs derived state is computed from. Touched on the main thread only.
    private var itemsById: Map<Long, MediaItem> = emptyMap()
    private var albums: List<Album> = emptyList()
    private var decided: Set<Long> = emptySet()
    private var pendingCount = 0
    private var excluded: Set<String> = emptySet()
    private var order = SortOrder.OLDEST_FIRST

    /** Settings writes run in call order, and a refresh reads settings only after earlier writes land. */
    private val settingsLock = Mutex()

    /**
     * Prune reads TRASHED ids then deletes the rows it did not find in the trash; a trash-now write
     * landing in between would be deleted, so the two never interleave.
     */
    private val trashedRowsLock = Mutex()

    private var refreshJob: Job? = null
    private var trashJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val access = accessLevel()
            settingsLock.withLock {
                excluded = settings.excludedAlbumIds.first()
                order = settings.order.first()
            }
            if (access == AccessLevel.NONE) {
                itemsById = emptyMap()
                albums = emptyList()
                _state.value = StartUiState(loading = false, access = AccessLevel.NONE, order = order)
                return@launch
            }
            _state.update { it.copy(loading = true, access = access) }
            // Only with FULL access: under PARTIAL, trashedItems() can't see trashed items outside the
            // user's selection, and pruning would forget rows for items that are still in the trash.
            if (access == AccessLevel.FULL) trashedRowsLock.withLock {
                val stillTrashed = media.trashedItems(decisions.idsIn(DecisionState.TRASHED)).map { it.item.id }
                decisions.pruneTrashed(stillTrashed.toSet())
            }
            setItems(media.allItems())
            reloadDecisions()
            publish()
        }
    }

    fun toggleAlbum(albumId: String) {
        val include = albumId in excluded
        excluded = if (include) excluded - albumId else excluded + albumId
        publish()
        persist { settings.setAlbumIncluded(albumId, include) }
    }

    fun setOrder(order: SortOrder) {
        this.order = order
        publish()
        persist { settings.setOrder(order) }
    }

    /** Trashes every visible PENDING_TRASH item; only items whose chunk succeeded become TRASHED. */
    fun onTrashPending(launcher: RequestLauncher) {
        if (trashJob?.isActive == true) return
        trashJob = viewModelScope.launch {
            val pending = decisions.idsIn(DecisionState.PENDING_TRASH).mapNotNull { itemsById[it] }
            if (pending.isEmpty()) return@launch
            val done = trasher.trash(pending, launcher)
            if (done.isNotEmpty()) {
                val doneIds = done.map { it.id }.toSet()
                trashedRowsLock.withLock { decisions.set(doneIds, DecisionState.TRASHED, DecisionSource.SWIPE) }
                setItems(itemsById.values.filter { it.id !in doneIds }) // no longer live

            }
            reloadDecisions()
            publish()
            if (done.size < pending.size) _state.update { it.copy(message = PENDING_NOT_TRASHED) }
        }
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun persist(write: suspend () -> Unit) {
        // UNDISPATCHED: take our place in the lock's queue now, ahead of any later refresh or write.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { settingsLock.withLock { write() } }
    }

    private fun setItems(items: List<MediaItem>) {
        itemsById = items.associateBy { it.id }
        albums = Library.albumsOf(items)
    }

    private suspend fun reloadDecisions() {
        decided = decisions.decidedIds()
        pendingCount = decisions.idsIn(DecisionState.PENDING_TRASH).count { it in itemsById }
    }

    /** Recomputes the UI state from the loaded library and the current settings. */
    private fun publish() {
        if (_state.value.access == AccessLevel.NONE) {
            _state.update { it.copy(order = order) }
            return
        }
        val rows = albums.map { AlbumRow(it, included = it.id !in excluded) }
        val queueCount = itemsById.values.count { it.albumId !in excluded && it.id !in decided }
        _state.update {
            it.copy(
                loading = false,
                albums = rows,
                selectedAlbums = rows.count { row -> row.included },
                queueCount = queueCount,
                order = order,
                pendingCount = pendingCount,
            )
        }
    }
}
