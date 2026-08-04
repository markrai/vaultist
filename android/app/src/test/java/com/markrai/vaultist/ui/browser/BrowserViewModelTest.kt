package com.markrai.vaultist.ui.browser

import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.BrowsePage
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.SearchPage
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private fun viewModel(repository: FakeVaultRepository = FakeVaultRepository()) =
        BrowserViewModel(repository)

    @Test
    fun filesSearchDebouncesAndPopulatesResults() = runTest(dispatcherRule.dispatcher) {
        val item = BrowseItem(BrowseKind.Note, "Folder/Vega", "Vega.md", "Vega", "Folder/Vega.md", null)
        val repository = FakeVaultRepository().apply {
            searchResult = VaultResult.Success(SearchPage(listOf(item), "100", "vega"))
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isSearchResults)

        viewModel.updateQuery("vega")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSearchResults)
        assertEquals(SearchMode.Files, repository.lastSearchMode)
        assertEquals("vega", repository.lastSearchQuery)
        assertEquals("Folder/Vega", viewModel.state.value.items.single().id)
    }

    @Test
    fun clearingFilesQueryRestoresBrowse() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.updateQuery("vega")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isSearchResults)

        viewModel.updateQuery("")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSearchResults)
        assertEquals("", viewModel.state.value.query)
    }

    @Test
    fun contentSearchRunsOnlyOnSubmit() = runTest(dispatcherRule.dispatcher) {
        val item = BrowseItem(BrowseKind.Note, "Notes/Idea", "Idea.md", "Idea", "Notes/Idea.md", null)
        val repository = FakeVaultRepository().apply {
            searchResult = VaultResult.Success(SearchPage(listOf(item), null, "secret"))
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.toggleSearchMode()
        advanceUntilIdle()
        assertEquals(SearchMode.Content, viewModel.state.value.searchMode)

        viewModel.updateQuery("secret")
        advanceTimeBy(500)
        advanceUntilIdle()
        assertNull(repository.lastSearchQuery)
        assertFalse(viewModel.state.value.isSearchResults)

        viewModel.submitSearch()
        advanceUntilIdle()
        assertEquals(SearchMode.Content, repository.lastSearchMode)
        assertEquals("secret", repository.lastSearchQuery)
        assertTrue(viewModel.state.value.isSearchResults)
        assertEquals("Notes/Idea", viewModel.state.value.items.single().id)
    }
}
