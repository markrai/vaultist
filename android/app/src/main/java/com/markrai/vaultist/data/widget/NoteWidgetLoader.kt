package com.markrai.vaultist.data.widget

import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.domain.VaultResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class NoteWidgetLoader @Inject constructor(
    private val repository: VaultRepository,
    private val preferences: NoteWidgetStore,
) {
    suspend fun load(appWidgetId: Int, boundNoteId: String? = null): NoteWidgetLoadResult {
        val noteId = boundNoteId?.takeIf { it.isNotBlank() }
            ?: preferences.getNoteId(appWidgetId)
            ?: return NoteWidgetLoadResult.Unbound
        val serverUrl = repository.serverUrl.first()
        if (serverUrl.isNullOrBlank()) return NoteWidgetLoadResult.ServerNotConfigured
        return when (val result = repository.getNote(noteId)) {
            is VaultResult.Success -> NoteWidgetLoadResult.Content(result.value)
            is VaultResult.Failure -> result.error.toWidgetLoadResult()
        }
    }
}
