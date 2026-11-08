package io.github.lronsmith.photosorter.ui

/** "1 copy", "0 copies", "2 copies": [count] followed by [one] when it is 1, else [other]. */
fun plural(count: Int, one: String, other: String): String = "$count ${if (count == 1) one else other}"
