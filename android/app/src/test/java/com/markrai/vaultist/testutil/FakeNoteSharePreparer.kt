package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.share.NoteSharePreparer
import com.markrai.vaultist.data.share.SharePayload

class FakeNoteSharePreparer : NoteSharePreparer {
    var lastNoteId: String? = null
    var lastFilename: String? = null
    var lastContent: String? = null
    var payload: SharePayload? = null
    var failure: Exception? = null

    override suspend fun prepare(noteId: String, filename: String, content: String): SharePayload {
        lastNoteId = noteId
        lastFilename = filename
        lastContent = content
        failure?.let { throw it }
        return payload ?: SharePayload(
            uri = android.net.Uri.parse("content://test/$filename"),
            filename = filename,
            mimeType = "text/markdown",
        )
    }
}
