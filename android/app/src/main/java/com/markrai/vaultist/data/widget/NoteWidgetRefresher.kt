package com.markrai.vaultist.data.widget

interface NoteWidgetRefresher {
    suspend fun refreshAll()
    fun scheduleRefreshWidget(appWidgetId: Int, noteId: String)
    suspend fun refreshWidget(appWidgetId: Int, noteId: String? = null): Boolean
    suspend fun refreshForNote(noteId: String)
    suspend fun clearAllAndRefresh()
}
