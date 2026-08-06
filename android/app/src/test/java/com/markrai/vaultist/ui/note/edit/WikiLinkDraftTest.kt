package com.markrai.vaultist.ui.note.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikiLinkDraftTest {
    @Test
    fun queryAtCursorReturnsPartialTargetInsideOpenWikiLink() {
        assertEquals("Other", WikiLinkDraft.queryAtCursor("See [[Other", 11))
    }

    @Test
    fun queryAtCursorReturnsEmptyStringImmediatelyAfterOpener() {
        assertEquals("", WikiLinkDraft.queryAtCursor("See [[", 6))
    }

    @Test
    fun queryAtCursorReturnsNullWhenLinkAlreadyClosed() {
        assertNull(WikiLinkDraft.queryAtCursor("See [[Done]] next", 13))
    }

    @Test
    fun openRangeCoversOpenerThroughCursor() {
        assertEquals(4 until 11, WikiLinkDraft.openRange("See [[Other", 11))
    }
}
