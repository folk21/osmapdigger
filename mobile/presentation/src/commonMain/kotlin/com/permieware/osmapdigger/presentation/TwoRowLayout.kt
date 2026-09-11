package com.permieware.osmapdigger.presentation

/** Split presentation items into at most two balanced rows while preserving source order. */
object TwoRowLayout {
    fun <T> split(items: List<T>): List<List<T>> {
        if (items.isEmpty()) return emptyList()
        val firstRowSize = (items.size + 1) / 2
        return listOf(items.take(firstRowSize), items.drop(firstRowSize)).filter { it.isNotEmpty() }
    }
}
