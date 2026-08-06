package com.markrai.vaultist.ui.widget

import com.markrai.vaultist.di.config.NoteWidgetConfig
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.ui.markdown.MarkdownBlock
import com.markrai.vaultist.ui.markdown.MarkdownDocumentParser

object WidgetMarkdownMapper {
    fun map(note: Note, config: NoteWidgetConfig): NoteWidgetContent {
        val parsed = MarkdownDocumentParser.parse(note.content)
        val blocks = mutableListOf<WidgetBlock>()
        var charactersUsed = 0

        for (block in parsed) {
            if (blocks.size >= config.maxBlocks) break
            val widgetBlock = block.toWidgetBlock() ?: continue
            val textLength = widgetBlock.displayLength()
            if (charactersUsed + textLength > config.maxCharacters) {
                val remaining = (config.maxCharacters - charactersUsed).coerceAtLeast(0)
                if (remaining == 0) break
                val truncated = truncateBlock(widgetBlock, remaining) ?: break
                blocks += truncated
                break
            }
            charactersUsed += textLength
            blocks += widgetBlock
        }

        return NoteWidgetContent(
            title = note.title.ifBlank { note.filename.removeSuffix(".md") },
            blocks = blocks,
        )
    }

    private fun MarkdownBlock.toWidgetBlock(): WidgetBlock? {
        val stableId = sourceLine.toLong()
        return when (this) {
            is MarkdownBlock.Heading -> {
                val text = text.trim()
                if (text.isEmpty()) null else WidgetBlock.Heading(level, text, stableId)
            }
            is MarkdownBlock.Paragraph -> {
                val text = text.trim()
                if (text.isEmpty()) null else WidgetBlock.Paragraph(text, stableId)
            }
            is MarkdownBlock.ListItem -> {
                val text = text.trim()
                if (text.isEmpty()) null else WidgetBlock.ListItem(text, ordered, stableId)
            }
            is MarkdownBlock.Quote -> {
                val text = text.trim()
                if (text.isEmpty()) null else WidgetBlock.Quote(text, stableId)
            }
            is MarkdownBlock.Code -> {
                val text = content.trim()
                if (text.isEmpty()) null else WidgetBlock.Code(text, stableId)
            }
        }
    }

    private fun WidgetBlock.displayLength(): Int = when (this) {
        is WidgetBlock.Heading -> text.length
        is WidgetBlock.Paragraph -> text.length
        is WidgetBlock.ListItem -> text.length
        is WidgetBlock.Quote -> text.length
        is WidgetBlock.Code -> text.length
    }

    private fun truncateBlock(block: WidgetBlock, maxChars: Int): WidgetBlock? {
        if (maxChars <= 0) return null
        val suffix = "…"
        return when (block) {
            is WidgetBlock.Heading -> block.copy(
                text = block.text.take(maxChars - suffix.length.coerceAtMost(maxChars)) + suffix,
            )
            is WidgetBlock.Paragraph -> block.copy(
                text = block.text.take(maxChars - suffix.length.coerceAtMost(maxChars)) + suffix,
            )
            is WidgetBlock.ListItem -> block.copy(
                text = block.text.take(maxChars - suffix.length.coerceAtMost(maxChars)) + suffix,
            )
            is WidgetBlock.Quote -> block.copy(
                text = block.text.take(maxChars - suffix.length.coerceAtMost(maxChars)) + suffix,
            )
            is WidgetBlock.Code -> block.copy(
                text = block.text.take(maxChars - suffix.length.coerceAtMost(maxChars)) + suffix,
            )
        }
    }
}
