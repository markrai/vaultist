package com.markrai.vaultist.ui.widget

import com.markrai.vaultist.di.config.NoteWidgetConfig
import com.markrai.vaultist.domain.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMarkdownMapperTest {
    private val note = Note(
        id = "demo",
        path = "demo.md",
        filename = "demo.md",
        title = "Demo",
        aliases = emptyList(),
        headings = emptyList(),
        links = emptyList(),
        attachments = emptyList(),
        modifiedAt = "2026-01-01T00:00:00Z",
        size = 1L,
        revision = "sha256:abc",
        content = """
            # Heading one

            Paragraph text.

            - list item
            > quote line

            ```kotlin
            val x = 1
            ```
        """.trimIndent(),
        error = null,
    )

    @Test
    fun mapsStructuredBlocks() {
        val content = WidgetMarkdownMapper.map(note, NoteWidgetConfig(maxBlocks = 20, maxCharacters = 10_000))
        assertEquals("Demo", content.title)
        assertTrue(content.blocks.any { it is WidgetBlock.Heading })
        assertTrue(content.blocks.any { it is WidgetBlock.Paragraph })
        assertTrue(content.blocks.any { it is WidgetBlock.ListItem })
        assertTrue(content.blocks.any { it is WidgetBlock.Quote })
        assertTrue(content.blocks.any { it is WidgetBlock.Code })
    }

    @Test
    fun emptyMarkdownYieldsNoBlocks() {
        val empty = note.copy(content = "")
        val content = WidgetMarkdownMapper.map(empty, NoteWidgetConfig())
        assertEquals(emptyList<WidgetBlock>(), content.blocks)
    }

    @Test
    fun blockBudgetTruncates() {
        val content = WidgetMarkdownMapper.map(note, NoteWidgetConfig(maxBlocks = 2, maxCharacters = 10_000))
        assertEquals(2, content.blocks.size)
    }

    @Test
    fun characterBudgetTruncates() {
        val content = WidgetMarkdownMapper.map(note, NoteWidgetConfig(maxBlocks = 20, maxCharacters = 20))
        val totalChars = content.blocks.sumOf {
            when (it) {
                is WidgetBlock.Heading -> it.text.length
                is WidgetBlock.Paragraph -> it.text.length
                is WidgetBlock.ListItem -> it.text.length
                is WidgetBlock.Quote -> it.text.length
                is WidgetBlock.Code -> it.text.length
            }
        }
        assertTrue(totalChars <= 20)
    }

    @Test
    fun stableIdsFollowSourceLines() {
        val first = WidgetMarkdownMapper.map(note, NoteWidgetConfig())
        val second = WidgetMarkdownMapper.map(note, NoteWidgetConfig())
        assertEquals(first.blocks.map { it.stableId }, second.blocks.map { it.stableId })
    }
}
