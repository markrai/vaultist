package com.vaultview.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test fun parsesCommonBlocksAndHidesFrontmatter() {
        val blocks = MarkdownDocumentParser.parse(
            """---
            title: Hidden
            ---
            # Heading

            Paragraph with **strong**.
            - item
            2. ordered
            > quote
            ```kotlin
            val x = 1
            ```
            """.trimIndent()
        )
        assertTrue(blocks.first() is MarkdownBlock.Heading)
        assertTrue(blocks.any { it is MarkdownBlock.Paragraph })
        assertTrue(blocks.any { it is MarkdownBlock.ListItem && it.ordered })
        assertTrue(blocks.last() is MarkdownBlock.Code)
        assertEquals("heading", headingSlug("Heading"))
    }

    @Test fun sourceLinesSupportHeadingAndBacklinkNavigation() {
        val blocks = MarkdownDocumentParser.parse("# One\n\ntext\n\n## Two")
        assertEquals(1, blocks.first().sourceLine)
        assertEquals(5, blocks.last().sourceLine)
    }
}
