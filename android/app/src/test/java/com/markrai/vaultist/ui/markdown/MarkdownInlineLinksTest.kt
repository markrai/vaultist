package com.markrai.vaultist.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.markrai.vaultist.domain.LinkResolution
import com.markrai.vaultist.domain.LinkStatus
import com.markrai.vaultist.domain.NoteLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownInlineLinksTest {
    private val linkColor = Color.Blue
    private val errorColor = Color.Red

    @Test
    fun externalMarkdownLinksAreStyledAndAnnotated() {
        val annotated = annotatedInline(
            text = "See [Example](https://example.com) for details.",
            links = emptyList(),
            linkColor = linkColor,
            errorColor = errorColor,
        )
        val urls = annotated.getStringAnnotations(0, annotated.length)
            .filter { it.tag == "url" }
            .map { it.item }
        assertEquals(listOf("https://example.com"), urls)
        assertEquals("See Example for details.", annotated.text)
        assertTrue(hasLinkStyle(annotated, "Example"))
    }

    @Test
    fun bareHttpsUrlsAreAutolinked() {
        val annotated = annotatedInline(
            text = "Open https://example.com/path now.",
            links = emptyList(),
            linkColor = linkColor,
            errorColor = errorColor,
        )
        val urls = annotated.getStringAnnotations(0, annotated.length)
            .filter { it.tag == "url" }
            .map { it.item }
        assertEquals(listOf("https://example.com/path"), urls)
        assertTrue(annotated.text.contains("https://example.com/path"))
        assertTrue(hasLinkStyle(annotated, "https://example.com/path"))
    }

    @Test
    fun angleBracketAutolinksAreRecognized() {
        val annotated = annotatedInline(
            text = "Go to <https://example.com> please.",
            links = emptyList(),
            linkColor = linkColor,
            errorColor = errorColor,
        )
        val urls = annotated.getStringAnnotations(0, annotated.length)
            .filter { it.tag == "url" }
            .map { it.item }
        assertEquals(listOf("https://example.com"), urls)
        assertEquals("Go to https://example.com please.", annotated.text)
    }

    @Test
    fun trailingPunctuationIsNotPartOfBareUrl() {
        val annotated = annotatedInline(
            text = "See https://example.com.",
            links = emptyList(),
            linkColor = linkColor,
            errorColor = errorColor,
        )
        val urls = annotated.getStringAnnotations(0, annotated.length)
            .filter { it.tag == "url" }
            .map { it.item }
        assertEquals(listOf("https://example.com"), urls)
        assertEquals("See https://example.com.", annotated.text)
    }

    @Test
    fun resolvedWikiLinksBecomeNoteAnnotations() {
        val annotated = annotatedInline(
            text = "Go to [[Other Note|label]] next.",
            links = listOf(
                NoteLink(
                    kind = "wiki",
                    raw = "Other Note|label",
                    target = "Other Note",
                    fragment = null,
                    display = "label",
                    line = 1,
                    column = 1,
                    context = null,
                    isEmbed = false,
                    isAsset = false,
                    resolution = LinkResolution(
                        status = LinkStatus.Resolved,
                        noteId = "folder/Other Note",
                        assetId = null,
                        candidates = emptyList(),
                    ),
                ),
            ),
            linkColor = linkColor,
            errorColor = errorColor,
        )
        val notes = annotated.getStringAnnotations(0, annotated.length)
            .filter { it.tag == "note" }
            .map { it.item }
        assertEquals(listOf("folder/Other Note\n"), notes)
        assertTrue(annotated.text.contains("label"))
        assertTrue(hasLinkStyle(annotated, "label"))
    }

    @Test
    fun matchingDestinationAllowsParenthesesInPath() {
        val text = "[x](https://example.com/foo(bar))"
        val end = matchingMarkdownDestinationEnd(text, text.indexOf('(') + 1)
        assertEquals(text.lastIndex, end)
    }

    @Test
    fun unmatchedWikiLinksRenderAsMissing() {
        val annotated = annotatedInline(
            text = "See [[Missing Note]] here.",
            links = emptyList(),
            linkColor = linkColor,
            errorColor = errorColor,
        )
        val missing = annotated.getStringAnnotations(0, annotated.length)
            .filter { it.tag == "missing" }
            .map { it.item }
        assertEquals(listOf("Missing Note"), missing)
        assertTrue(hasLinkStyle(annotated, "Missing Note", errorColor))
    }

    @Test
    fun linksForBacklinkContextMarksWikiSyntaxResolved() {
        val links = linksForBacklinkContext(
            context = "Ref [[Target|label]] end",
            targetNoteId = "Folder/Target",
            fragment = null,
            display = "label",
            occurrenceKind = "wiki",
        )
        val annotated = annotatedInline(
            text = "Ref [[Target|label]] end",
            links = links,
            linkColor = linkColor,
            errorColor = errorColor,
        )
        assertTrue(hasLinkStyle(annotated, "label", linkColor))
    }

    private fun hasLinkStyle(
        annotated: androidx.compose.ui.text.AnnotatedString,
        label: String,
        color: Color = linkColor,
    ): Boolean {
        val start = annotated.text.indexOf(label)
        require(start >= 0) { "label not found: $label" }
        return annotated.spanStyles.any { range ->
            range.start <= start && range.end >= start + label.length &&
                range.item.textDecoration == TextDecoration.Underline &&
                range.item.color == color
        }
    }

    private fun hasLinkStyle(annotated: androidx.compose.ui.text.AnnotatedString, label: String): Boolean =
        hasLinkStyle(annotated, label, linkColor)
}
