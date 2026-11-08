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
import io.github.lronsmith.photosorter.trash.trashVerified
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * What the swipe screen shows; [upcoming] are the next 2 items (preloaded). [sessionTrashed]
 * counts items this session actually moved to the system trash; pending ones are in [pendingCount].
 */
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
internal const val GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again."

/**
 * Owns the swipe queue for one session. The queue changes at once on every swipe; decision
 * writes and trash/restore requests run on one worker, one at a time in submission order, so
 * rapid swipes are all recorded in order and an undo waits for that item's in-flight trash.
 *
 * The worker runs in [workerScope], which must outlive this ViewModel (application scope) and
 * run on the main thread: jobs already queued when the screen closes are still written.
 * All state is touched only on the main thread.
 */
class SwipeViewModel(
    private val media: MediaSource,
    private val decisions: DecisionStore,
    private val settings: SettingsStore,
    private val trasher: Trasher,
    workerScope: CoroutineScope,
) : ViewModel() {
    private val _state = MutableStateFlow(SwipeUiState())
    val state: StateFlow<SwipeUiState> = _state.asStateFlow()

    private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private var queue = SwipeQueue(emptyList())

    /** Mirrors the queue's undo stack, so [onUndo] can see the next action before reversing it. */
    private val history = ArrayDeque<SwipeAction>()

    /** Ids with a keep or trash swipe in effect (not undone): no longer in the queue. */
    private val decidedThisSession = mutableSetOf<Long>()

    /** The whole library at load time, by id (pending items from any album may be trashed); null until loaded. */
    private val library = MutableStateFlow<Map<Long, MediaItem>?>(null)

    /**
     * Ids this session moved to the system trash, net of undo; undoing their swipe needs a restore.
     * Its size is the session's "trashed" count: pending items aren't trashed yet.
     */
    private val trashedThisSession = mutableSetOf<Long>()

    /** An undo of a trash swipe is waiting for its job; swipes and further undos are ignored. */
    private var undoing = false
    private var pendingCount = 0
    private var undecodable = emptySet<Long>()
    private var sessionKept = 0
    private var message: String? = null

    init {
        workerScope.launch {
            for (job in jobs) {
                try {
                    job()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep the worker alive: later decisions must still be written.
                    undoing = false
                    message = GENERIC_ERROR_MESSAGE
                    publish()
                }
            }
        }
        viewModelScope.launch {
            // The chip counts only pending items in the loaded library: the others can't be trashed.
            combine(decisions.observeCounts(), library.filterNotNull()) { _, lib -> lib }
                .collectLatest { lib ->
                    try {
                        pendingCount = decisions.idsIn(DecisionState.PENDING_TRASH).count { it in lib }
                        publish()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = GENERIC_ERROR_MESSAGE
                        publish()
                    }
                }
        }
        submit { load() }
    }

    fun onSwipe(direction: SwipeDirection, launcher: RequestLauncher) {
        if (undoing || queue.current == null) return
        val action = when (direction) {
            SwipeDirection.RIGHT -> queue.keep().also {
                sessionKept++
                decidedThisSession += it.item.id
                submit { decisions.set(listOf(it.item.id), DecisionState.KEPT, DecisionSource.SWIPE) }
            }
            SwipeDirection.LEFT -> queue.trash().also {
                decidedThisSession += it.item.id
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
                commitUndo(action)
            }
            is SwipeAction.Later -> commitUndo(action)
            is SwipeAction.Trash -> {
                undoing = true
                publish()
                submit { undoTrash(action, launcher) }
            }
        }
    }

    fun onTrashPending(launcher: RequestLauncher) {
        submit { trashPending(launcher) }
    }

    /** A card failed to load: drop it if it was deleted elsewhere, else show the placeholder. */
    fun onLoadFailed(item: MediaItem) {
        submit {
            if (media.exists(item)) {
                undecodable = undecodable + item.id
            } else if (item.id !in decidedThisSession) {
                // Only while still queued: a swipe since then may have trashed it (which also reads as gone).
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

    override fun onCleared() {
        // No new jobs; those already queued still run in workerScope.
        jobs.close()
    }

    private fun submit(job: suspend () -> Unit) {
        jobs.trySend(job)
    }

    private suspend fun load() {
        val items = media.allItems()
        val excluded = settings.excludedAlbumIds.first()
        val order = settings.order.first()
        val decided = decisions.decidedIds()
        // PENDING_TRASH items are decided, so they stay out of the queue but count in the chip.
        queue = SwipeQueue(Library.filterAndSort(items, excluded, order).filter { it.id !in decided })
        library.value = items.associateBy { it.id }
        publish()
    }

    private fun submitTrash(item: MediaItem, launcher: RequestLauncher) {
        // Checked per swipe: Media management can be revoked mid-session.
        if (!trasher.canTrashInstantly()) {
            submit { decisions.set(listOf(item.id), DecisionState.PENDING_TRASH, DecisionSource.SWIPE) }
            return
        }
        submit {
            val trashed = trasher.trashVerified(listOf(item), launcher, media).isNotEmpty()
            if (trashed) {
                decisions.set(listOf(item.id), DecisionState.TRASHED, DecisionSource.SWIPE)
                trashedThisSession += item.id
                publish()
            } else {
                decisions.set(listOf(item.id), DecisionState.PENDING_TRASH, DecisionSource.SWIPE)
                message = INSTANT_FAILED_MESSAGE
                publish()
            }
        }
    }

    /** Runs after every earlier job, so an instant trash of the item has finished by now. */
    private suspend fun undoTrash(action: SwipeAction.Trash, launcher: RequestLauncher) {
        val item = action.item
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
        decisions.clear(listOf(item.id))
        undoing = false
        commitUndo(action)
    }

    private suspend fun trashPending(launcher: RequestLauncher) {
        val lib = library.value ?: return
        // Pending ids outside the loaded library aren't counted in the chip and can't be trashed now.
        val items = decisions.idsIn(DecisionState.PENDING_TRASH).mapNotNull { lib[it] }
        if (items.isEmpty()) return
        val trashed = trasher.trashVerified(items, launcher, media)
        if (trashed.isNotEmpty()) {
            val trashedIds = trashed.map { it.id }
            decisions.set(trashedIds, DecisionState.TRASHED, DecisionSource.SWIPE)
            trashedThisSession += trashedIds
            publish()
        }
        if (trashed.size < items.size) {
            message = BATCH_FAILED_MESSAGE
            publish()
        }
    }

    /** Reverses [action] in the queue, if it is still the latest one. */
    private fun commitUndo(action: SwipeAction) {
        if (history.lastOrNull() === action) {
            history.removeLast()
            queue.undo()
            if (action !is SwipeAction.Later) decidedThisSession -= action.item.id
        }
        publish()
    }

    private fun publish() {
        val loaded = library.value != null
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
            sessionTrashed = trashedThisSession.size,
            message = message,
        )
    }
}
