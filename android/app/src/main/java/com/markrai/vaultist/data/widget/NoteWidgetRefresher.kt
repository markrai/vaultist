package com.markrai.vaultist.data.widget

interface NoteWidgetRefresher {
    suspend fun refreshAll()
    suspend fun refreshWidget(appWidgetId: Int)
    suspend fun refreshForNote(noteId: String)
    suspend fun clearAllAndRefresh()
}
