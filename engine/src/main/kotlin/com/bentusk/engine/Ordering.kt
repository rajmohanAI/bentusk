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
