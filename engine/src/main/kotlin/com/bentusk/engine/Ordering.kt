package com.bentusk.engine

/** Moves the item at [from] to [to], shifting the items between them. Out-of-range moves return the list unchanged. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val result = toMutableList()
    result.add(to, result.removeAt(from))
    return result
}

/** Names exported files so they sort in posting order in any gallery. */
object ExportNaming {
    fun tileFileName(prefix: String, sessionStamp: String, tile: Tile, total: Int, extension: String): String {
        val width = total.toString().length
        val order = tile.postOrder.toString().padStart(width, '0')
        return "${prefix}_${sessionStamp}_${order}of$total.$extension"
    }
}

/** How a profile grid orders posts: pinned posts first, then the rest, each group keeping its own order. */
object PinnedGrid {
    fun <T> order(items: List<T>, pinned: (T) -> Boolean): List<T> {
        val (top, rest) = items.partition(pinned)
        return top + rest
    }

    /** Whether one more post can be pinned when [pinnedCount] already are. */
    fun canPin(pinnedCount: Int) = pinnedCount < InstagramSpec.MAX_PINNED
}
