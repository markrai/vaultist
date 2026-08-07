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

    @Test fun parsesUncheckedTaskListItem() {
        val item = MarkdownDocumentParser.parse("- [ ] Buy milk").single() as MarkdownBlock.ListItem
        assertEquals(false, item.checked)
        assertEquals("Buy milk", item.text)
        assertEquals(false, item.ordered)
    }

    @Test fun parsesCheckedTaskListItemWithLowerAndUpperX() {
        val lower = MarkdownDocumentParser.parse("- [x] Done").single() as MarkdownBlock.ListItem
        assertEquals(true, lower.checked)
        assertEquals("Done", lower.text)

        val upper = MarkdownDocumentParser.parse("* [X] Also done").single() as MarkdownBlock.ListItem
        assertEquals(true, upper.checked)
        assertEquals("Also done", upper.text)
    }

    @Test fun parsesOrderedTaskListItem() {
        val item = MarkdownDocumentParser.parse("1. [ ] First step").single() as MarkdownBlock.ListItem
        assertEquals(false, item.checked)
        assertEquals("First step", item.text)
        assertTrue(item.ordered)
        assertEquals(1, item.number)
    }

    @Test fun plainListItemsHaveNullChecked() {
        val normal = MarkdownDocumentParser.parse("- item").single() as MarkdownBlock.ListItem
        assertEquals(null, normal.checked)
        assertEquals("item", normal.text)

        val bracketText = MarkdownDocumentParser.parse("- [not a box]").single() as MarkdownBlock.ListItem
        assertEquals(null, bracketText.checked)
        assertEquals("[not a box]", bracketText.text)
    }
}
