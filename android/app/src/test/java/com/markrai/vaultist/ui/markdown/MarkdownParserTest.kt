package com.markrai.vaultist.ui.markdown

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
        val vectors = JSONArray(dialectFixtureDir().resolve("heading-slugs.json").readText())
        val headingVector = (0 until vectors.length())
            .map { vectors.getJSONObject(it) }
            .first { it.getString("input") == "Heading" }
        assertEquals(headingVector.getString("slug"), headingSlug("Heading"))
    }

    @Test fun sourceLinesSupportHeadingAndBacklinkNavigation() {
        val blocks = MarkdownDocumentParser.parse("# One\n\ntext\n\n## Two")
        assertEquals(1, blocks.first().sourceLine)
        assertEquals(5, blocks.last().sourceLine)
    }
}
