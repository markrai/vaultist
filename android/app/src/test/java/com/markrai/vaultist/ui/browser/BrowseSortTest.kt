package com.markrai.vaultist.ui.browser

import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.BrowseSortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseSortTest {
    private fun note(
        id: String,
        title: String,
        path: String = "$id.md",
        modifiedAt: String? = null,
    ) = BrowseItem(BrowseKind.Note, id, "$title.md", title, path, null, modifiedAt)

    private fun folder(name: String) = BrowseItem(BrowseKind.Folder, null, name, null, name, null)

    @Test
    fun alphabeticalSortsFoldersFirstThenTitleAscending() {
        val items = listOf(
            note("b", "Beta"),
            folder("Projects"),
            note("a", "Alpha"),
            folder("Archive"),
        )
        val sorted = sortBrowseItems(items, BrowseSortMode.Alphabetical)
        assertEquals(
            listOf("Archive", "Projects", "a", "b"),
            sorted.map { it.id ?: it.path },
        )
    }

    @Test
    fun modifiedDescSortsNewestFirstWithTitleAndPathTieBreakers() {
        val items = listOf(
            note("b", "Beta", modifiedAt = "2026-01-02T00:00:00Z"),
            note("a", "Alpha", modifiedAt = "2026-01-02T00:00:00Z"),
            note("c", "Charlie", modifiedAt = "2026-01-03T00:00:00Z"),
        )
        val sorted = sortBrowseItems(items, BrowseSortMode.ModifiedDesc)
        assertEquals(listOf("c", "a", "b"), sorted.map { it.id })
    }

    @Test
    fun missingModifiedAtSortsAfterKnownTimestamps() {
        val items = listOf(
            note("missing", "Missing"),
            note("dated", "Dated", modifiedAt = "2026-01-02T00:00:00Z"),
        )
        val sorted = sortBrowseItems(items, BrowseSortMode.ModifiedDesc)
        assertEquals(listOf("dated", "missing"), sorted.map { it.id })
    }

    @Test
    fun invalidModifiedAtTreatedAsMissing() {
        assertEquals(null, parseModifiedAt("not-a-date"))
    }
}
