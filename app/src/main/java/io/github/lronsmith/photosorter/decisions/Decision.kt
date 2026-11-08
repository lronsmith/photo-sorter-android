package io.github.lronsmith.photosorter.decisions

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DecisionState { KEPT, PENDING_TRASH, TRASHED }

enum class DecisionSource { SWIPE, DUPLICATE }

/** What the user decided about one MediaStore item; [decidedAt] is epoch ms. */
@Entity(tableName = "decisions")
data class Decision(
    @PrimaryKey val mediaId: Long,
    val state: DecisionState,
    val source: DecisionSource,
    val decidedAt: Long,
)

data class DecisionCounts(val kept: Int, val pending: Int, val trashed: Int)
