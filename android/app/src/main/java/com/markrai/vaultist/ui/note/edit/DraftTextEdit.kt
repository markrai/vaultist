package com.markrai.vaultist.ui.note.edit

object DraftTextEdit {
    fun insertAtSelection(draft: NoteEditDraft, inserted: String): NoteEditDraft {
        val start = draft.selectionStart.coerceIn(0, draft.text.length)
        val end = draft.selectionEnd.coerceIn(start, draft.text.length)
        val text = draft.text.replaceRange(start, end, inserted)
        val cursor = start + inserted.length
        return NoteEditDraft(text, cursor, cursor)
    }

    fun replaceRange(draft: NoteEditDraft, range: IntRange, replacement: String): NoteEditDraft {
        val start = range.first.coerceIn(0, draft.text.length)
        val end = range.last.coerceIn(start, draft.text.length) + 1
        val text = draft.text.replaceRange(start, end, replacement)
        val cursor = start + replacement.length
        return NoteEditDraft(text, cursor, cursor)
    }
}
