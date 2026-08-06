package com.markrai.vaultist.ui.widget

import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.BrowsePage
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.testutil.FakeNoteWidgetStore
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteWidgetConfigureViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private lateinit var preferences: FakeNoteWidgetStore
    private lateinit var repository: FakeVaultRepository
    private lateinit var viewModel: NoteWidgetConfigureViewModel

    @Before
    fun setUp() {
        preferences = FakeNoteWidgetStore()
        repository = FakeVaultRepository()
        viewModel = NoteWidgetConfigureViewModel(
            repository,
            preferences,
            com.markrai.vaultist.di.config.BrowseUiConfig(debounceMs = 1),
        )
    }

    @Test
    fun initializeLoadsRootNotesWhenServerConfigured() = runTest(dispatcherRule.dispatcher) {
        repository.listNotesResult = VaultResult.Success(
            BrowsePage(
                items = listOf(
                    BrowseItem(BrowseKind.Note, "a", "a.md", "A", "a.md", null),
                    BrowseItem(BrowseKind.Folder, null, "folder", null, "folder", null),
                ),
                nextCursor = null,
                folder = "",
            ),
        )
        viewModel.initialize(10)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.serverConfigured)
        assertEquals(1, viewModel.state.value.items.size)
        assertEquals("a", viewModel.state.value.items.single().id)
    }

    @Test
    fun confirmBindingStoresSelectedNote() = runTest(dispatcherRule.dispatcher) {
        viewModel.initialize(10)
        advanceUntilIdle()
        viewModel.selectNote("notes/test")
        assertEquals("notes/test", viewModel.confirmBinding())
        assertEquals("notes/test", preferences.getNoteId(10))
    }

    @Test
    fun confirmWithoutSelectionFails() = runTest(dispatcherRule.dispatcher) {
        viewModel.initialize(10)
        advanceUntilIdle()
        assertNull(viewModel.confirmBinding())
    }

    @Test
    fun searchFiltersToNotes() = runTest(dispatcherRule.dispatcher) {
        repository.searchResult = VaultResult.Success(
            com.markrai.vaultist.domain.SearchPage(
                items = listOf(
                    BrowseItem(BrowseKind.Note, "n1", "n1.md", "N1", "n1.md", null),
                    BrowseItem(BrowseKind.Folder, null, "dir", null, "dir", null),
                ),
                nextCursor = null,
                query = "n",
            ),
        )
        viewModel.initialize(10)
        advanceUntilIdle()
        viewModel.updateQuery("n")
        advanceUntilIdle()
        assertEquals(SearchMode.Files, repository.lastSearchMode)
        assertEquals(1, viewModel.state.value.items.size)
    }
}
