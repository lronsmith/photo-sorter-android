package io.github.lronsmith.photosorter.ui.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lronsmith.photosorter.decisions.DecisionSource
import io.github.lronsmith.photosorter.decisions.DecisionState
import io.github.lronsmith.photosorter.decisions.DecisionStore
import io.github.lronsmith.photosorter.media.Library
import io.github.lronsmith.photosorter.media.MediaSource
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.prefs.SettingsStore
import io.github.lronsmith.photosorter.queue.SwipeAction
import io.github.lronsmith.photosorter.queue.SwipeQueue
import io.github.lronsmith.photosorter.trash.RequestLauncher
import io.github.lronsmith.photosorter.trash.Trasher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the swipe screen shows; [upcoming] are the next 2 items (preloaded). */
data class SwipeUiState(
    val loading: Boolean = true,
    val current: MediaItem? = null,
    val upcoming: List<MediaItem> = emptyList(),
    val decided: Int = 0,
    val total: Int = 0,
    val pendingCount: Int = 0,
    val canUndo: Boolean = false,
    val undecodable: Set<Long> = emptySet(),
    val done: Boolean = false,
    val sessionKept: Int = 0,
    val sessionTrashed: Int = 0,
    val message: String? = null,
)

internal const val BATCH_FAILED_MESSAGE = "Some items weren't trashed. They're still waiting."
internal const val INSTANT_FAILED_MESSAGE = "Couldn't trash that item. It's waiting in the batch."
internal const val RESTORE_FAILED_MESSAGE = "Couldn't restore that item."

/**
 * Owns the swipe queue for one session. The queue changes at once on every swipe; decision
 * writes and trash/restore requests run on one worker, one at a time in submission order, so
 * rapid swipes are all recorded in order and an undo waits for that item's in-flight trash.
 *
 * All state is touched only on the main thread (viewModelScope and the UI callbacks).
 */
class SwipeViewModel(
    private val media: MediaSource,
    private val decisions: DecisionStore,
    private val settings: SettingsStore,
    private val trasher: Trasher,
) : ViewModel() {
    private val _state = MutableStateFlow(SwipeUiState())
    val state: StateFlow<SwipeUiState> = _state.asStateFlow()

    private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private var queue = SwipeQueue(emptyList())

    /** Mirrors the queue's undo stack, so [onUndo] can see the next action before reversing it. */
    private val history = ArrayDeque<SwipeAction>()

    /** The whole library at load time, by id (pending items from any album may be trashed). */
    private var library: Map<Long, MediaItem> = emptyMap()

    /** Ids this session moved to the system trash; undoing their swipe needs a restore. */
    private val trashedThisSession = mutableSetOf<Long>()

    private var loaded = false

    /** An undo of a trash swipe is waiting for its job; swipes and further undos are ignored. */
    private var undoing = false
    private var pendingCount = 0
    private var undecodable = emptySet<Long>()
    private var sessionKept = 0
    private var sessionTrashed = 0
    private var message: String? = null

    init {
        viewModelScope.launch { for (job in jobs) job() }
        viewModelScope.launch {
            decisions.observeCounts().collect {
                pendingCount = it.pending
                publish()
            }
        }
        submit { load() }
    }

    fun onSwipe(direction: SwipeDirection, launcher: RequestLauncher) {
        if (undoing || queue.current == null) return
        val action = when (direction) {
            SwipeDirection.RIGHT -> queue.keep().also {
                sessionKept++
                submit { decisions.set(listOf(it.item.id), DecisionState.KEPT, DecisionSource.SWIPE) }
            }
            SwipeDirection.LEFT -> queue.trash().also {
                sessionTrashed++
                submitTrash(it.item, launcher)
            }
            SwipeDirection.UP -> queue.later()
        }
        history.addLast(action)
        publish()
    }

    fun onUndo(launcher: RequestLauncher) {
        if (undoing) return
        when (val action = history.lastOrNull() ?: return) {
            is SwipeAction.Keep -> {
                sessionKept--
                submit { decisions.clear(listOf(action.item.id)) }
                commitUndo()
            }
            is SwipeAction.Later -> commitUndo()
            is SwipeAction.Trash -> {
                undoing = true
                publish()
                submit { undoTrash(action.item, launcher) }
            }
        }
    }

    fun onTrashPending(launcher: RequestLauncher) {
        submit { trashPending(launcher) }
    }

    /** A card failed to load: drop it if it was deleted elsewhere, else show the placeholder. */
    fun onLoadFailed(item: MediaItem) {
        viewModelScope.launch {
            if (media.exists(item)) {
                undecodable = undecodable + item.id
            } else {
                queue.drop(item.id)
                history.removeAll { it.item.id == item.id }
            }
            publish()
        }
    }

    fun onMessageShown() {
        message = null
        publish()
    }

    private fun submit(job: suspend () -> Unit) {
        jobs.trySend(job)
    }

    private suspend fun load() {
        val items = media.allItems()
        val excluded = settings.excludedAlbumIds.first()
        val order = settings.order.first()
        val decided = decisions.decidedIds()
        library = items.associateBy { it.id }
        // PENDING_TRASH items are decided, so they stay out of the queue but count in the chip.
        queue = SwipeQueue(Library.filterAndSort(items, excluded, order).filter { it.id !in decided })
        loaded = true
        publish()
    }

    private fun submitTrash(item: MediaItem, launcher: RequestLauncher) {
        // Checked per swipe: Media management can be revoked mid-session.
        if (!trasher.canTrashInstantly()) {
            submit { decisions.set(listOf(item.id), DecisionState.PENDING_TRASH, DecisionSource.SWIPE) }
            return
        }
        submit {
            val trashed = trasher.trash(listOf(item), launcher).isNotEmpty()
            // Record a completed request even if the screen is closing.
            withContext(NonCancellable) {
                if (trashed) {
                    decisions.set(listOf(item.id), DecisionState.TRASHED, DecisionSource.SWIPE)
                    trashedThisSession += item.id
                } else {
                    decisions.set(listOf(item.id), DecisionState.PENDING_TRASH, DecisionSource.SWIPE)
                    message = INSTANT_FAILED_MESSAGE
                    publish()
                }
            }
        }
    }

    /** Runs after every earlier job, so an instant trash of [item] has finished by now. */
    private suspend fun undoTrash(item: MediaItem, launcher: RequestLauncher) {
        if (item.id in trashedThisSession) {
            val restored = trasher.restore(listOf(item), launcher).isNotEmpty()
            if (!restored) {
                undoing = false
                message = RESTORE_FAILED_MESSAGE
                publish()
                return
            }
            trashedThisSession -= item.id
        }
        withContext(NonCancellable) { decisions.clear(listOf(item.id)) }
        undoing = false
        sessionTrashed--
        commitUndo()
    }

    private suspend fun trashPending(launcher: RequestLauncher) {
        val ids = decisions.idsIn(DecisionState.PENDING_TRASH)
        if (ids.isEmpty()) return
        // Ids missing from the library can't be trashed now; they stay pending.
        val items = ids.mapNotNull { library[it] }
        val trashed = if (items.isEmpty()) emptyList() else trasher.trash(items, launcher)
        withContext(NonCancellable) {
            if (trashed.isNotEmpty()) {
                val trashedIds = trashed.map { it.id }
                decisions.set(trashedIds, DecisionState.TRASHED, DecisionSource.SWIPE)
                trashedThisSession += trashedIds
            }
            if (trashed.size < ids.size) {
                message = BATCH_FAILED_MESSAGE
                publish()
            }
        }
    }

    private fun commitUndo() {
        history.removeLast()
        queue.undo()
        publish()
    }

    private fun publish() {
        _state.value = SwipeUiState(
            loading = !loaded,
            current = queue.current,
            upcoming = queue.peekNext(2),
            decided = queue.decided,
            total = queue.total,
            pendingCount = pendingCount,
            canUndo = queue.canUndo && !undoing,
            undecodable = undecodable,
            done = loaded && queue.current == null,
            sessionKept = sessionKept,
            sessionTrashed = sessionTrashed,
            message = message,
        )
    }
}
