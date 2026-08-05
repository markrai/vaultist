package com.markrai.vaultist.data.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.markrai.vaultist.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultNoteSharePreparer @Inject constructor(
    @ApplicationContext private val context: Context,
) : NoteSharePreparer {
    internal var uriProvider: ((File) -> Uri)? = null

    override suspend fun prepare(noteId: String, filename: String, content: String): SharePayload =
        withContext(Dispatchers.IO) {
            val safeFilename = sanitizeFilename(filename)
            val noteDir = File(context.cacheDir, "share/notes/${encodeNoteId(noteId)}")
            noteDir.mkdirs()
            val file = File(noteDir, safeFilename)
            file.writeText(content, Charsets.UTF_8)
            val uri = (uriProvider ?: ::defaultUriForFile)(file)
            SharePayload(uri, safeFilename, MIME_TYPE)
        }

    private fun defaultUriForFile(file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )

    private fun sanitizeFilename(filename: String): String {
        val base = filename
            .replace('\\', '/')
            .substringAfterLast('/')
            .filter { it != '\u0000' }
            .ifBlank { DEFAULT_FILENAME }
        return if (base.endsWith(".md", ignoreCase = true)) base else "$base.md"
    }

    private fun encodeNoteId(noteId: String): String =
        noteId.replace('/', '_').replace('\\', '_')

    companion object {
        private const val DEFAULT_FILENAME = "note.md"
        private const val MIME_TYPE = "text/markdown"
    }
}
