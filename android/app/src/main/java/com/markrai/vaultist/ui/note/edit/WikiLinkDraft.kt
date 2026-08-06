package com.markrai.vaultist.ui.note.edit

object WikiLinkDraft {
    private const val Open = "[["

    /** Returns the in-progress wiki target before [cursor], or null when not inside an open link. */
    fun queryAtCursor(text: String, cursor: Int): String? {
        val openStart = openStart(text, cursor) ?: return null
        return text.substring(openStart + Open.length, cursor)
    }

    /** Span from `[[` through [cursor) to replace when accepting a suggestion. */
    fun openRange(text: String, cursor: Int): IntRange? {
        val openStart = openStart(text, cursor) ?: return null
        return openStart until cursor
    }

    private fun openStart(text: String, cursor: Int): Int? {
        if (cursor < Open.length) return null
        val searchEnd = (cursor - Open.length).coerceAtLeast(0)
        val openIndex = text.lastIndexOf(Open, searchEnd)
        if (openIndex < 0) return null
        val closeIndex = text.indexOf("]]", openIndex + Open.length)
        if (closeIndex in (openIndex + Open.length) until cursor) return null
        return openIndex
    }
}
