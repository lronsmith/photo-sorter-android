package io.github.lronsmith.photosorter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lronsmith.photosorter.decisions.DecisionState
import io.github.lronsmith.photosorter.decisions.DecisionStore
import io.github.lronsmith.photosorter.media.MediaSource
import io.github.lronsmith.photosorter.media.TrashedItem
import io.github.lronsmith.photosorter.trash.RequestLauncher
import io.github.lronsmith.photosorter.trash.Trasher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrashedUiState(
    val loading: Boolean = true,
    val items: List<TrashedItem> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val restoring: Boolean = false,
    val message: String? = null,
)

/** The items this app trashed that are still in the system trash, soonest purge first; restores them. */
class TrashedViewModel(
    private val media: MediaSource,
    private val decisions: DecisionStore,
    private val trasher: Trasher,
) : ViewModel() {
    private val _state = MutableStateFlow(TrashedUiState())
    val state: StateFlow<TrashedUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /**
     * (Re)reads the list. Skipped while a restore runs: its system dialogs pause and resume the
     * screen, and the restore updates the list itself when it finishes.
     */
    fun load() {
        if (_state.value.restoring) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val ids = decisions.idsIn(DecisionState.TRASHED)
            val items = media.trashedItems(ids).sortedBy { it.expiresAt }
            val present = items.mapTo(HashSet()) { it.item.id }
            _state.update { it.copy(loading = false, items = items, selected = it.selected intersect present) }
        }
    }

    fun toggle(id: Long) {
        _state.update { s -> s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id) }
    }

    fun selectAll() {
        _state.update { s -> s.copy(selected = s.items.mapTo(HashSet()) { it.item.id }) }
    }

    fun restoreSelected(launcher: RequestLauncher) {
        val s = _state.value
        restore(s.items.filter { it.item.id in s.selected }, launcher)
    }

    fun restoreAll(launcher: RequestLauncher) = restore(_state.value.items, launcher)

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun restore(targets: List<TrashedItem>, launcher: RequestLauncher) {
        if (targets.isEmpty() || _state.value.restoring) return
        // A load that finishes after this restore would bring back rows read before it.
        loadJob?.cancel()
        _state.update { it.copy(restoring = true) }
        viewModelScope.launch {
            try {
                val restored = trasher.restore(targets.map { it.item }, launcher).mapTo(HashSet()) { it.id }
                decisions.clear(restored)
                _state.update { s ->
                    s.copy(
                        items = s.items.filterNot { it.item.id in restored },
                        selected = s.selected - restored,
                        message = if (restored.size < targets.size) "Some items couldn't be restored." else s.message,
                    )
                }
            } finally {
                _state.update { it.copy(restoring = false) }
            }
        }
    }
}
