package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.widget.NoteWidgetRefresher
import com.markrai.vaultist.data.widget.NoteWidgetStore

class FakeNoteWidgetStore : NoteWidgetStore {
    private val bindings = mutableMapOf<Int, String>()

    override suspend fun getNoteId(appWidgetId: Int): String? = bindings[appWidgetId]

    override suspend fun setBinding(appWidgetId: Int, noteId: String) {
        bindings[appWidgetId] = noteId
    }

    override suspend fun removeBinding(appWidgetId: Int) {
        bindings.remove(appWidgetId)
    }

    override suspend fun findAppWidgetIdsForNote(noteId: String): List<Int> =
        bindings.filterValues { it == noteId }.keys.sorted()

    override suspend fun removeAll() {
        bindings.clear()
    }
}

class FakeNoteWidgetRefresher : NoteWidgetRefresher {
    val refreshedNotes = mutableListOf<String>()
    val refreshedWidgetIds = mutableListOf<Int>()
    var clearAllCalls = 0

    override suspend fun refreshAll() = Unit

    override suspend fun refreshWidget(appWidgetId: Int) {
        refreshedWidgetIds += appWidgetId
    }

    override suspend fun refreshForNote(noteId: String) {
        refreshedNotes += noteId
    }

    override suspend fun clearAllAndRefresh() {
        clearAllCalls++
    }
}
