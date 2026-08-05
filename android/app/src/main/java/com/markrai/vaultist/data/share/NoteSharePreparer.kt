package com.markrai.vaultist.data.share

interface NoteSharePreparer {
    suspend fun prepare(noteId: String, filename: String, content: String): SharePayload
}
