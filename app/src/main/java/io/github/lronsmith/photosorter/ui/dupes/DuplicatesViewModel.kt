package io.github.lronsmith.photosorter.ui.dupes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lronsmith.photosorter.decisions.DecisionSource
import io.github.lronsmith.photosorter.decisions.DecisionState
import io.github.lronsmith.photosorter.decisions.DecisionStore
import io.github.lronsmith.photosorter.dupes.DuplicateFinder
import io.github.lronsmith.photosorter.dupes.DuplicateGroup
import io.github.lronsmith.photosorter.dupes.ScanProgress
import io.github.lronsmith.photosorter.media.MediaSource
import io.github.lronsmith.photosorter.trash.RequestLauncher
import io.github.lronsmith.photosorter.trash.Trasher
import io.github.lronsmith.photosorter.trash.trashVerified
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DuplicatesUiState {
    data object Idle : DuplicatesUiState

    /** [progress] is null until the finder reports for the first time. */
    data class Scanning(val progress: ScanProgress?) : DuplicatesUiState

    /** Empty [groups] means "No duplicates found". */
    data class Results(
        val groups: List<DuplicateGroup>,
        val unreadableCount: Int,
        val trashing: Boolean = false,
        val message: String? = null,
    ) : DuplicatesUiState {
        /** Every copy except each group's keeper. */
        val copiesToTrash: Int = groups.sumOf { it.copies.size - 1 }
        val bytesFreed: Long = groups.sumOf { it.bytesFreed }
    }
}

/** Scans the whole library for byte-identical copies and trashes every non-keeper. */
class DuplicatesViewModel(
    private val media: MediaSource,
    private val decisions: DecisionStore,
    private val trasher: Trasher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow<DuplicatesUiState>(DuplicatesUiState.Idle)
    val state: StateFlow<DuplicatesUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    /** Starts a scan of the whole library; does nothing while a scan or a trash is running. */
    fun scan() {
        if (scanJob?.isActive == true || (_state.value as? DuplicatesUiState.Results)?.trashing == true) return
        _state.value = DuplicatesUiState.Scanning(null)
        scanJob = viewModelScope.launch {
            // The finder reports once per 64 KiB buffer on the IO thread; it only overwrites this
            // conflated flow, and the main-thread sampler below publishes whatever is latest.
            // The sampler belongs to this job, so nothing is published once the scan is cancelled.
            val progress = MutableStateFlow<ScanProgress?>(null)
            val sampler = launch {
                progress.collect { if (it != null) _state.value = DuplicatesUiState.Scanning(it) }
            }
            val result = try {
                withContext(ioDispatcher) {
                    DuplicateFinder.scan(media.allItems(), media::openStream) { progress.value = it }
                }
            } finally {
                sampler.cancel()
            }
            _state.value = DuplicatesUiState.Results(result.groups, result.unreadableCount)
        }
    }

    /** Stops a running scan and returns to [DuplicatesUiState.Idle]. */
    fun cancel() {
        scanJob?.cancel()
        scanJob = null
        _state.value = DuplicatesUiState.Idle
    }

    /** Makes [itemId] the copy kept in the group with [hash]. Ignored while trashing. */
    fun setKeeper(hash: String, itemId: Long) {
        val results = _state.value as? DuplicatesUiState.Results ?: return
        if (results.trashing) return
        _state.value = results.copy(
            groups = results.groups.map { group ->
                if (group.hash == hash && group.copies.any { it.id == itemId }) group.copy(keeperId = itemId) else group
            },
        )
    }

    /**
     * Trashes every non-keeper of every group in one request. Copies now in the system trash
     * are recorded as TRASHED (source DUPLICATE) and leave the list; a group whose copies are
     * all gone except its keeper is dropped.
     */
    fun trashAll(launcher: RequestLauncher) {
        val results = _state.value as? DuplicatesUiState.Results ?: return
        if (results.trashing || results.copiesToTrash == 0) return
        _state.value = results.copy(trashing = true, message = null)
        viewModelScope.launch {
            val nonKeepers = results.groups.flatMap { group -> group.copies.filter { it.id != group.keeperId } }
            val trashedIds = trasher.trashVerified(nonKeepers, launcher, media).mapTo(HashSet()) { it.id }
            if (trashedIds.isNotEmpty()) {
                decisions.set(trashedIds, DecisionState.TRASHED, DecisionSource.DUPLICATE)
            }
            val remaining = results.groups.mapNotNull { group ->
                val copies = group.copies.filter { it.id !in trashedIds }
                if (copies.size > 1) group.copy(copies = copies) else null
            }
            _state.value = results.copy(
                groups = remaining,
                trashing = false,
                message = if (trashedIds.size < nonKeepers.size) PARTIAL_TRASH_MESSAGE else null,
            )
        }
    }

    /** The screen has shown [DuplicatesUiState.Results.message]. */
    fun onMessageShown() {
        val results = _state.value as? DuplicatesUiState.Results ?: return
        if (results.message != null) _state.value = results.copy(message = null)
    }

    /** Leaving the screen cancels the scan. */
    override fun onCleared() {
        scanJob?.cancel()
    }

    private companion object {
        const val PARTIAL_TRASH_MESSAGE = "Some copies weren't trashed."
    }
}
