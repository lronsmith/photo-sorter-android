package io.github.lronsmith.photosorter.ui.settings

private const val DAY_MS = 86_400_000L

/** Whole days until [expiresAtMs] (floored), never negative. */
fun daysLeft(expiresAtMs: Long, nowMs: Long): Int =
    Math.floorDiv(expiresAtMs - nowMs, DAY_MS).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

/** Tile label for [days] left: "today" for 0, else e.g. "6d". */
fun daysLeftLabel(days: Int): String = if (days == 0) "today" else "${days}d"
