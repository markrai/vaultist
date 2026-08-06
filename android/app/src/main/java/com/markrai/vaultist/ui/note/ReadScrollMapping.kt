package com.markrai.vaultist.ui.note

import androidx.compose.ui.text.TextLayoutResult

/** Read-mode viewport anchor captured while browsing a note. */
data class ReadScrollAnchor(
    val sourceLine: Int = 1,
    /** Pixels of the first visible read block scrolled past the viewport top. */
    val partialScrollOffsetPx: Int = 0,
)

/** Maps read-mode scroll anchors to raw Markdown character offsets for edit entry. */
object ReadScrollMapping {
    /** [line] is 1-based, matching [com.markrai.vaultist.ui.markdown.MarkdownBlock.sourceLine]. */
    fun characterOffsetAtLine(content: String, line: Int): Int {
        if (line <= 1) return 0
        var currentLine = 1
        var index = 0
        while (index < content.length && currentLine < line) {
            if (content[index] == '\n') currentLine++
            index++
        }
        return index.coerceIn(0, content.length)
    }

    /** Scroll offset in the edit field that aligns [characterOffset]'s line with read-mode viewport top. */
    fun editScrollOffsetPx(
        layout: TextLayoutResult,
        characterOffset: Int,
        partialScrollOffsetPx: Int,
    ): Int {
        if (layout.layoutInput.text.isEmpty()) return 0
        val safeOffset = characterOffset.coerceIn(0, layout.layoutInput.text.length)
        val line = layout.getLineForOffset(safeOffset)
        val lineTop = layout.getLineTop(line).toInt()
        return (lineTop + partialScrollOffsetPx.coerceAtLeast(0)).coerceAtLeast(0)
    }
}
