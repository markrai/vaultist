package com.markrai.vaultist.ui.note.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class DraftTextEditTest {
    @Test
    fun insertAtSelectionReplacesSelectionAndMovesCursor() {
        val draft = NoteEditDraft("hello world", 5, 5)
        val updated = DraftTextEdit.insertAtSelection(draft, "[[")
        assertEquals("hello[[ world", updated.text)
        assertEquals(7, updated.selectionStart)
        assertEquals(7, updated.selectionEnd)
    }

    @Test
    fun replaceRangeCompletesWikiLink() {
        val draft = NoteEditDraft("See [[Oth", 9, 9)
        val updated = DraftTextEdit.replaceRange(draft, 4 until 9, "[[Folder/Other]]")
        assertEquals("See [[Folder/Other]]", updated.text)
        assertEquals(20, updated.selectionStart)
    }
}
