package io.github.lronsmith.photosorter.queue

import io.github.lronsmith.photosorter.model.MediaItem

/** One swipe on the swipe screen; [SwipeQueue.undo] returns the action it reversed. */
sealed interface SwipeAction {
    val item: MediaItem

    data class Keep(override val item: MediaItem) : SwipeAction
    data class Trash(override val item: MediaItem) : SwipeAction
    data class Later(override val item: MediaItem) : SwipeAction
}

/**
 * The ordered cards of the swipe screen. [keep], [trash] and [later] act on [current] and
 * push an undo entry; [undo] reverses them one at a time back to the start. Every swipe and
 * undo is O(1); only [drop] and [peekNext] walk the queue.
 */
class SwipeQueue(items: List<MediaItem>) {
    private val queue = ArrayDeque(items)
    private val undoStack = ArrayDeque<SwipeAction>()

    val current: MediaItem? get() = queue.firstOrNull()

    /** Items still to sort, including [current]. */
    val remaining: Int get() = queue.size

    /** Initial size minus dropped items. */
    var total: Int = items.size
        private set

    /** Keep and trash actions currently in effect (undone ones and `later` do not count). */
    var decided: Int = 0
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** The [n] items after [current]. */
    fun peekNext(n: Int): List<MediaItem> = queue.asSequence().drop(1).take(n).toList()

    fun keep(): SwipeAction.Keep = decide(SwipeAction.Keep(takeCurrent()))

    fun trash(): SwipeAction.Trash = decide(SwipeAction.Trash(takeCurrent()))

    fun later(): SwipeAction.Later {
        val action = SwipeAction.Later(takeCurrent())
        queue.addLast(action.item)
        undoStack.addLast(action)
        return action
    }

    /** Reverses the latest action; afterwards [current] is the returned action's item. */
    fun undo(): SwipeAction? {
        val action = undoStack.removeLastOrNull() ?: return null
        when (action) {
            is SwipeAction.Keep, is SwipeAction.Trash -> decided--
            // Every action after this `later` has been undone (or dropped with its item),
            // so its item is still the last one in the queue.
            is SwipeAction.Later -> queue.removeLast()
        }
        queue.addFirst(action.item)
        return action
    }

    /** Forgets the item with [id] (e.g. it was deleted elsewhere): queue position and undo entries. */
    fun drop(id: Long) {
        val inQueue = queue.removeAll { it.id == id }
        // An item is either still queued or has exactly one keep/trash entry in effect.
        val decisions = undoStack.count { it.item.id == id && it !is SwipeAction.Later }
        undoStack.removeAll { it.item.id == id }
        decided -= decisions
        if (inQueue || decisions > 0) total--
    }

    private fun takeCurrent(): MediaItem =
        queue.removeFirstOrNull() ?: throw IllegalStateException("The queue is empty")

    private fun <A : SwipeAction> decide(action: A): A {
        undoStack.addLast(action)
        decided++
        return action
    }
}
