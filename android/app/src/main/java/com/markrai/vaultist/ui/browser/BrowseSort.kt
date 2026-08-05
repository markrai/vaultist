package com.markrai.vaultist.ui.browser

import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.BrowseSortMode
import java.time.Instant
import java.time.format.DateTimeParseException

fun sortBrowseItems(items: List<BrowseItem>, mode: BrowseSortMode): List<BrowseItem> {
    val folders = items.filter { it.kind == BrowseKind.Folder }.sortedWith(folderComparator())
    val notes = items.filter { it.kind == BrowseKind.Note }.sortedWith(noteComparator(mode))
    return folders + notes
}

private fun folderComparator() = compareBy<BrowseItem>({ it.name.lowercase() }, { it.path })

private fun displayTitle(item: BrowseItem) = (item.title ?: item.name).lowercase()

private fun noteComparator(mode: BrowseSortMode) = when (mode) {
    BrowseSortMode.Alphabetical -> compareBy<BrowseItem>(
        { displayTitle(it) },
        { it.path },
        { it.id.orEmpty() },
    )
    BrowseSortMode.ModifiedDesc -> modifiedDescNoteComparator()
}

private fun modifiedDescNoteComparator() = Comparator<BrowseItem> { a, b ->
    val aInstant = parseModifiedAt(a.modifiedAt)
    val bInstant = parseModifiedAt(b.modifiedAt)
    when {
        aInstant != null && bInstant != null -> {
            val cmp = bInstant.compareTo(aInstant)
            if (cmp != 0) return@Comparator cmp
        }
        aInstant != null -> return@Comparator -1
        bInstant != null -> return@Comparator 1
    }
    val titleCmp = displayTitle(a).compareTo(displayTitle(b))
    if (titleCmp != 0) return@Comparator titleCmp
    val pathCmp = a.path.compareTo(b.path)
    if (pathCmp != 0) return@Comparator pathCmp
    a.id.orEmpty().compareTo(b.id.orEmpty())
}

internal fun parseModifiedAt(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}
