package io.github.lronsmith.photosorter.queue

import io.github.lronsmith.photosorter.fakes.testItem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwipeQueueTest {
    private fun q(n: Int) = SwipeQueue((1L..n).map { testItem(it) })

    @Test fun keepAndTrashAdvance() { val s = q(3); assertEquals(1L, s.keep().item.id); assertEquals(2L, s.trash().item.id); assertEquals(3L, s.current?.id); assertEquals(2, s.decided); assertEquals(1, s.remaining) }
    @Test fun laterMovesToBack() { val s = q(3); s.later(); assertEquals(listOf(2L, 3L, 1L), listOf(s.current!!.id) + s.peekNext(2).map { it.id }); assertEquals(0, s.decided) }
    @Test fun laterOnSingleItemKeepsIt() { val s = q(1); s.later(); assertEquals(1L, s.current?.id) }
    @Test fun undoKeepRestoresCurrent() { val s = q(3); s.keep(); val u = s.undo(); assertTrue(u is SwipeAction.Keep); assertEquals(1L, s.current?.id); assertEquals(0, s.decided) }
    @Test fun undoLaterBringsItemBackToFront() { val s = q(3); s.later(); s.undo(); assertEquals(listOf(1L, 2L, 3L), listOf(s.current!!.id) + s.peekNext(2).map { it.id }) }
    @Test fun undoChainsBackToStart() { val s = q(4); s.keep(); s.later(); s.trash(); repeat(3) { assertNotNull(s.undo()) }; assertNull(s.undo()); assertFalse(s.canUndo); assertEquals(1L, s.current?.id); assertEquals(4, s.remaining) }
    @Test fun emptyQueue() { val s = q(0); assertNull(s.current); assertFailsWith<IllegalStateException> { s.keep() }; assertNull(s.undo()) }
    @Test fun finishingQueueEndsWithNullCurrent() { val s = q(2); s.keep(); s.trash(); assertNull(s.current); assertEquals(0, s.remaining); assertEquals(2, s.decided); s.undo(); assertEquals(2L, s.current?.id) }
    @Test fun dropRemovesItemAndItsUndoEntries() { val s = q(3); s.keep(); s.drop(1); s.drop(3); assertNull(s.undo()); assertEquals(2L, s.current?.id); assertEquals(1, s.total) }
    @Test(timeout = 2000) fun twentyThousandItemsStayFast() { val s = q(20_000); repeat(20_000) { s.later() }; repeat(20_000) { s.undo() }; repeat(20_000) { s.keep() }; assertEquals(20_000, s.decided); assertNull(s.current) }
}
