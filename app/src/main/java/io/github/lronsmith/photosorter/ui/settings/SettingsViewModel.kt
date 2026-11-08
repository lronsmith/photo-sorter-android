package io.github.lronsmith.photosorter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lronsmith.photosorter.decisions.DecisionStore
import io.github.lronsmith.photosorter.media.AccessLevel
import io.github.lronsmith.photosorter.trash.Trasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val access: AccessLevel = AccessLevel.NONE,
    val manageMediaSupported: Boolean = false,
    val instantTrash: Boolean = false,
    val trashedCount: Int = 0,
    val versionLabel: String = "",
)

/**
 * Permission status, the Trashed count and Reset decisions. Permissions are granted in Android's
 * own settings, so the screen calls [refresh] on resume to re-read them.
 */
class SettingsViewModel(
    private val decisions: DecisionStore,
    private val trasher: Trasher,
    private val accessLevel: () -> AccessLevel,
    sdkInt: Int,
    versionLabel: String,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsUiState(manageMediaSupported = sdkInt >= 31, versionLabel = versionLabel),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            decisions.observeCounts().collect { counts -> _state.update { it.copy(trashedCount = counts.trashed) } }
        }
    }

    fun refresh() {
        _state.update { it.copy(access = accessLevel(), instantTrash = trasher.canTrashInstantly()) }
    }

    /** Forgets KEPT and PENDING_TRASH decisions; TRASHED rows stay because they back the Trashed list. */
    fun resetDecisions() {
        viewModelScope.launch { decisions.resetKeptAndPending() }
    }
}
