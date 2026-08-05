package com.markrai.vaultist.ui.note

import com.markrai.vaultist.domain.Note
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot handoff so create can open the editor from the POST 201 body
 * without an immediate GET against a stale index snapshot.
 */
@Singleton
class NoteOpenSeed @Inject constructor() {
    private val lock = Any()
    private var pending: Note? = null

    fun offer(note: Note) {
        synchronized(lock) { pending = note }
    }

    fun consume(id: String): Note? = synchronized(lock) {
        val note = pending
        if (note?.id == id) {
            pending = null
            note
        } else {
            null
        }
    }
}
