package com.markrai.vaultist.ui.widget

import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.ui.note.toggleTaskLine

/** Flips a GFM task marker on [sourceLine] and saves. Returns false when no change was applied. */
suspend fun toggleWidgetTask(repository: VaultRepository, noteId: String, sourceLine: Int): Boolean {
    val note = (repository.getNote(noteId) as? VaultResult.Success)?.value ?: return false
    val updated = toggleTaskLine(note.content, sourceLine) ?: return false
    return repository.updateNote(note.id, note.revision, updated) is VaultResult.Success
}
