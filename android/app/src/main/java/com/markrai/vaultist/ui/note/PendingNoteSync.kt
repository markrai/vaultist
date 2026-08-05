package com.markrai.vaultist.ui.note

import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot signal that a note should reload link resolutions when the user returns
 * after creating a missing-link target from [com.markrai.vaultist.ui.note.NoteScreen].
 */
@Singleton
class PendingNoteSync @Inject constructor() {
    private val lock = Any()
    private var noteId: String? = null

    fun offerReload(noteId: String) {
        synchronized(lock) { this.noteId = noteId }
    }

    fun consumeReload(expectedNoteId: String): Boolean = synchronized(lock) {
        if (noteId == expectedNoteId) {
            noteId = null
            true
        } else {
            false
        }
    }
}
