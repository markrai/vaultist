package com.markrai.vaultist.ui.note.edit

data class NoteEditDraft(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
) {
    init {
        require(selectionStart in 0..text.length) { "selectionStart out of range" }
        require(selectionEnd in 0..text.length) { "selectionEnd out of range" }
    }

    companion object {
        fun atEnd(text: String) = NoteEditDraft(text, text.length, text.length)
    }
}
