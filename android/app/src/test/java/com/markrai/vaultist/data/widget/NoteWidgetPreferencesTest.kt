package com.markrai.vaultist.data.widget

import com.markrai.vaultist.testutil.FakeNoteWidgetStore
import com.markrai.vaultist.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteWidgetPreferencesTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private lateinit var preferences: FakeNoteWidgetStore

    @Before
    fun setUp() {
        preferences = FakeNoteWidgetStore()
    }

    @Test
    fun bindsDifferentNotesToDifferentWidgets() = runTest(dispatcherRule.dispatcher) {
        preferences.setBinding(1, "notes/a")
        preferences.setBinding(2, "notes/b")
        assertEquals("notes/a", preferences.getNoteId(1))
        assertEquals("notes/b", preferences.getNoteId(2))
    }

    @Test
    fun rebindingOneWidgetDoesNotAffectAnother() = runTest(dispatcherRule.dispatcher) {
        preferences.setBinding(1, "notes/a")
        preferences.setBinding(2, "notes/b")
        preferences.setBinding(1, "notes/c")
        assertEquals("notes/c", preferences.getNoteId(1))
        assertEquals("notes/b", preferences.getNoteId(2))
    }

    @Test
    fun removeBindingClearsOnlyThatWidget() = runTest(dispatcherRule.dispatcher) {
        preferences.setBinding(1, "notes/a")
        preferences.setBinding(2, "notes/b")
        preferences.removeBinding(1)
        assertNull(preferences.getNoteId(1))
        assertEquals("notes/b", preferences.getNoteId(2))
    }

    @Test
    fun unknownWidgetReturnsNull() = runTest(dispatcherRule.dispatcher) {
        assertNull(preferences.getNoteId(99))
    }

    @Test
    fun findAppWidgetIdsForNoteReturnsMatches() = runTest(dispatcherRule.dispatcher) {
        preferences.setBinding(1, "notes/a")
        preferences.setBinding(2, "notes/a")
        preferences.setBinding(3, "notes/b")
        assertEquals(listOf(1, 2), preferences.findAppWidgetIdsForNote("notes/a"))
    }

    @Test
    fun removeAllClearsBindings() = runTest(dispatcherRule.dispatcher) {
        preferences.setBinding(1, "notes/a")
        preferences.setBinding(2, "notes/b")
        preferences.removeAll()
        assertNull(preferences.getNoteId(1))
        assertNull(preferences.getNoteId(2))
    }
}
