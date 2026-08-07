package com.markrai.vaultist.ui.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskLineToggleTest {
    @Test
    fun togglesUncheckedToChecked() {
        val content = "- [ ] Buy milk"
        assertEquals("- [x] Buy milk", toggleTaskLine(content, 1))
    }

    @Test
    fun togglesCheckedLowerXToUnchecked() {
        val content = "- [x] Done"
        assertEquals("- [ ] Done", toggleTaskLine(content, 1))
    }

    @Test
    fun togglesCheckedUpperXToUnchecked() {
        val content = "* [X] Done"
        assertEquals("* [ ] Done", toggleTaskLine(content, 1))
    }

    @Test
    fun togglesOrderedTaskLine() {
        val content = "1. [ ] First step"
        assertEquals("1. [x] First step", toggleTaskLine(content, 1))
    }

    @Test
    fun preservesIndentAndTrailingText() {
        val content = "  - [ ] Nested item with text"
        assertEquals("  - [x] Nested item with text", toggleTaskLine(content, 1))
    }

    @Test
    fun returnsNullForNonTaskLine() {
        assertNull(toggleTaskLine("- plain item", 1))
        assertNull(toggleTaskLine("- [not a box]", 1))
    }

    @Test
    fun returnsNullForOutOfRangeLine() {
        assertNull(toggleTaskLine("- [ ] item", 0))
        assertNull(toggleTaskLine("- [ ] item", 2))
    }

    @Test
    fun togglesOnlyRequestedLineInMultilineNote() {
        val content = "- [ ] One\n- [x] Two"
        assertEquals("- [x] One\n- [x] Two", toggleTaskLine(content, 1))
        assertEquals("- [ ] One\n- [ ] Two", toggleTaskLine(content, 2))
    }
}
