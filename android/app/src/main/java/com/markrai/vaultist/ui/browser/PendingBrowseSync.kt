package com.markrai.vaultist.ui.browser

import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.Note
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BrowseMutation {
    data class UpsertNote(val note: Note) : BrowseMutation
    data class UpsertFolder(val folder: BrowseItem) : BrowseMutation
    data class DeleteNote(val noteId: String) : BrowseMutation
    data class DeleteFolder(val path: String) : BrowseMutation
}

/**
 * Cross-screen browse mutations (create, save, delete) consumed when returning to browse.
 */
@Singleton
class PendingBrowseSync @Inject constructor() {
    private val lock = Any()
    private val pending = mutableListOf<BrowseMutation>()

    fun offer(mutation: BrowseMutation) {
        synchronized(lock) {
            when (mutation) {
                is BrowseMutation.UpsertNote -> {
                    pending.removeAll {
                        (it is BrowseMutation.DeleteNote && it.noteId == mutation.note.id) ||
                            (it is BrowseMutation.UpsertNote && it.note.id == mutation.note.id)
                    }
                    pending.add(mutation)
                }
                is BrowseMutation.UpsertFolder -> {
                    pending.removeAll {
                        (it is BrowseMutation.UpsertFolder && it.folder.path == mutation.folder.path) ||
                            (it is BrowseMutation.DeleteFolder && it.path == mutation.folder.path)
                    }
                    pending.add(mutation)
                }
                is BrowseMutation.DeleteNote -> {
                    pending.removeAll {
                        (it is BrowseMutation.UpsertNote && it.note.id == mutation.noteId) ||
                            (it is BrowseMutation.DeleteNote && it.noteId == mutation.noteId)
                    }
                    pending.add(mutation)
                }
                is BrowseMutation.DeleteFolder -> {
                    pending.removeAll {
                        (it is BrowseMutation.UpsertFolder && it.folder.path == mutation.path) ||
                            (it is BrowseMutation.DeleteFolder && it.path == mutation.path)
                    }
                    pending.add(mutation)
                }
            }
        }
    }

    fun drain(): List<BrowseMutation> = synchronized(lock) {
        pending.toList().also { pending.clear() }
    }

    fun offerAfterDelete(noteId: String) {
        offer(BrowseMutation.DeleteNote(noteId))
    }

    fun offerAfterDeleteFolder(path: String) {
        offer(BrowseMutation.DeleteFolder(path))
    }
}
