package com.markrai.vaultist.ui.browser

import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot signal so note delete can clear/reload browse when returning from [com.markrai.vaultist.ui.note.NoteScreen].
 */
@Singleton
class PendingBrowseSync @Inject constructor() {
    private val lock = Any()
    private var deletedNoteId: String? = null

    fun offerAfterDelete(noteId: String) {
        synchronized(lock) { deletedNoteId = noteId }
    }

    fun consumeAfterDelete(): String? = synchronized(lock) {
        val id = deletedNoteId
        deletedNoteId = null
        id
    }
}
