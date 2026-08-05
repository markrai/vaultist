package com.markrai.vaultist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteNamingTest {
    @Test
    fun noteIdFromTitleUsesCurrentFolder() {
        val result = noteIdFromTitle("Folder", "My Note")
        assertEquals("Folder/My Note", result.id)
        assertEquals("My Note", result.leaf)
        assertNull(result.error)
    }

    @Test
    fun noteIdFromTitleUsesVaultRootWhenFolderEmpty() {
        val result = noteIdFromTitle("", "Root Note")
        assertEquals("Root Note", result.id)
    }

    @Test
    fun sanitizeNoteLeafRejectsInvalidTitles() {
        assertNull(sanitizeNoteLeaf(""))
        assertNull(sanitizeNoteLeaf("   "))
        assertNull(sanitizeNoteLeaf("."))
        assertNull(sanitizeNoteLeaf(".."))
    }

    @Test
    fun sanitizeNoteLeafReplacesInvalidCharacters() {
        assertEquals("My-Note", sanitizeNoteLeaf("My/Note"))
        assertEquals("Title", sanitizeNoteLeaf("  Title  "))
    }
}
