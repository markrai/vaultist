package com.vaultview.ui.search

import com.vaultview.domain.BrowseItem
import com.vaultview.domain.BrowseKind
import com.vaultview.domain.SearchPage
import com.vaultview.domain.VaultResult
import com.vaultview.testutil.FakeVaultRepository
import com.vaultview.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test fun exposesBoundedSearchResultsAndCursor() = runTest(dispatcherRule.dispatcher) {
        val item = BrowseItem(BrowseKind.Note, "Folder/Vega", "Vega.md", "Vega", "Folder/Vega.md", null)
        val repository = FakeVaultRepository().apply {
            searchResult = VaultResult.Success(SearchPage(listOf(item), "100", "vega"))
        }
        val viewModel = SearchViewModel(repository)
        viewModel.updateQuery("vega")
        viewModel.search()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.loading)
        assertEquals("Folder/Vega", viewModel.state.value.results.single().id)
        assertEquals("100", viewModel.state.value.nextCursor)
    }
}
