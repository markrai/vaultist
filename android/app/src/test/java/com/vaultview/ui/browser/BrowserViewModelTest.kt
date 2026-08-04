package com.vaultview.ui.browser

import com.vaultview.data.ask.VaultAskEngine
import com.vaultview.data.genai.LocalAiCapability
import com.vaultview.domain.BrowseItem
import com.vaultview.domain.BrowseKind
import com.vaultview.domain.BrowsePage
import com.vaultview.domain.Note
import com.vaultview.domain.SearchMode
import com.vaultview.domain.SearchPage
import com.vaultview.domain.VaultResult
import com.vaultview.testutil.FakeAskPreferences
import com.vaultview.testutil.FakePromptGenerationClient
import com.vaultview.testutil.FakeVaultRepository
import com.vaultview.testutil.MainDispatcherRule
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

    init {
        BrowserViewModel.askWorkDispatcher = dispatcherRule.dispatcher
    }

    private fun viewModel(
        repository: FakeVaultRepository = FakeVaultRepository(),
        prompt: FakePromptGenerationClient = FakePromptGenerationClient(),
    ) = BrowserViewModel(repository, VaultAskEngine(repository, prompt, FakeAskPreferences()), prompt)

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

    @Test
    fun askSubmitGeneratesAnswerWithSources() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            filesSearchResult = VaultResult.Success(
                SearchPage(
                    listOf(BrowseItem(BrowseKind.Note, "Notes/A", "A.md", "Alpha", "Notes/A.md", null)),
                    null,
                    "alpha",
                ),
            )
            contentSearchResult = VaultResult.Success(SearchPage(emptyList(), null, "alpha"))
            noteResult = VaultResult.Success(
                Note("Notes/A", "Notes/A.md", "A.md", "Alpha", emptyList(), emptyList(), emptyList(), emptyList(), "1", "Alpha note about deployment.", null),
            )
        }
        val prompt = FakePromptGenerationClient()
        val viewModel = viewModel(repository, prompt)
        advanceUntilIdle()

        repeat(2) { viewModel.toggleSearchMode() }
        advanceUntilIdle()
        assertEquals(SearchMode.Ask, viewModel.state.value.searchMode)

        viewModel.updateQuery("deployment")
        viewModel.submitSearch()
        advanceUntilIdle()

        assertEquals("deployment", viewModel.state.value.submittedQuestion)
        assertEquals("Answer [1].", viewModel.state.value.askAnswer)
        assertEquals(1, viewModel.state.value.askSources.size)
        assertEquals(1, prompt.generateCalls)
    }

    @Test
    fun staleAskRequestDoesNotPublishLateAnswer() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            filesSearchResult = VaultResult.Success(
                SearchPage(listOf(BrowseItem(BrowseKind.Note, "Notes/A", "A.md", "Alpha", "Notes/A.md", null)), null, "alpha"),
            )
            contentSearchResult = VaultResult.Success(SearchPage(emptyList(), null, "alpha"))
            noteResult = VaultResult.Success(
                Note("Notes/A", "Notes/A.md", "A.md", "Alpha", emptyList(), emptyList(), emptyList(), emptyList(), "1", "Alpha note.", null),
            )
        }
        val prompt = FakePromptGenerationClient()
        val viewModel = viewModel(repository, prompt)
        advanceUntilIdle()

        repeat(2) { viewModel.toggleSearchMode() }
        viewModel.updateQuery("alpha")
        viewModel.submitSearch()
        viewModel.toggleSearchMode()
        advanceUntilIdle()

        assertNull(viewModel.state.value.askAnswer)
        assertFalse(viewModel.state.value.askSubmitting)
    }
}
