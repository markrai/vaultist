package com.markrai.vaultist.ui.note

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadScrollMappingTest {
    @Test
    fun firstLineStartsAtZero() {
        assertEquals(0, ReadScrollMapping.characterOffsetAtLine("alpha\nbeta", 1))
    }

    @Test
    fun mapsOneBasedLineToCharacterOffset() {
        val content = "line one\nline two\nline three"
        assertEquals(0, ReadScrollMapping.characterOffsetAtLine(content, 1))
        assertEquals(9, ReadScrollMapping.characterOffsetAtLine(content, 2))
        assertEquals(18, ReadScrollMapping.characterOffsetAtLine(content, 3))
    }

    @Test
    fun pastEndLineClampsToContentLength() {
        assertEquals(5, ReadScrollMapping.characterOffsetAtLine("hello", 99))
    }
}
