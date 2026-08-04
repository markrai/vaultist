package com.markrai.vaultist.ui.markdown

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

internal fun dialectFixtureDir(): File {
    val candidates = listOf(
        File(System.getProperty("user.dir"), "fixtures/markdown"),
        File(System.getProperty("user.dir"), "../fixtures/markdown"),
        File(System.getProperty("user.dir"), "../../fixtures/markdown"),
        File(System.getProperty("user.dir"), "../../../fixtures/markdown"),
    )
    return candidates.firstOrNull { File(it, "heading-slugs.json").isFile }
        ?: error("fixtures/markdown not found from user.dir=${System.getProperty("user.dir")}")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DialectFixturesTest {
    @Test
    fun headingSlugVectorsMatchServer() {
        val vectors = JSONArray(dialectFixtureDir().resolve("heading-slugs.json").readText())
        for (index in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(index)
            val input = vector.getString("input")
            val expected = vector.getString("slug")
            assertEquals("input=$input", expected, headingSlug(input))
        }
    }

    @Test
    fun headingSlugsFixtureHasExpectedHeadings() {
        val markdown = dialectFixtureDir().resolve("heading-slugs.md").readText()
        val headings = MarkdownDocumentParser.parse(markdown).filterIsInstance<MarkdownBlock.Heading>()
        assertEquals(5, headings.size)
        assertTrue(headings.any { it.text == "First Heading" && it.level == 1 })
        assertTrue(headings.any { it.text == "A: Better Title" && it.level == 2 })
    }
}
