package com.markrai.vaultist.data.widget

interface NoteWidgetStore {
    suspend fun getNoteId(appWidgetId: Int): String?
    suspend fun setBinding(appWidgetId: Int, noteId: String)
    suspend fun removeBinding(appWidgetId: Int)
    suspend fun findAppWidgetIdsForNote(noteId: String): List<Int>
    suspend fun removeAll()
}
