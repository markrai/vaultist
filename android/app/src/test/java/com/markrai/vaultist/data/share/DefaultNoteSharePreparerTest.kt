package com.markrai.vaultist.data.share

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultNoteSharePreparerTest {
    @Test
    fun prepareWritesMarkdownFileAndReturnsSharePayload() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preparer = DefaultNoteSharePreparer(context).apply {
            uriProvider = { file -> Uri.parse("content://test/${file.name}") }
        }

        val payload = preparer.prepare("Folder/Note", "Note.md", "# Hello")

        assertEquals("Note.md", payload.filename)
        assertEquals("text/markdown", payload.mimeType)
        assertNotNull(payload.uri)
        val file = File(context.cacheDir, "share/notes/Folder_Note/Note.md")
        assertTrue(file.exists())
        assertEquals("# Hello", file.readText())
    }

    @Test
    fun prepareEnsuresMdExtension() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preparer = DefaultNoteSharePreparer(context).apply {
            uriProvider = { file -> Uri.parse("content://test/${file.name}") }
        }

        val payload = preparer.prepare("Plain", "Plain", "body")

        assertEquals("Plain.md", payload.filename)
    }
}
