package com.markrai.vaultist.ui.ask

import com.markrai.vaultist.data.ask.VaultAskEngine
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.SearchPage
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.testutil.FakeAskPreferences
import com.markrai.vaultist.testutil.FakePromptGenerationClient
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AskViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private fun viewModel(
        repository: FakeVaultRepository = FakeVaultRepository(),
        prompt: FakePromptGenerationClient = FakePromptGenerationClient(),
    ): AskViewModel {
        val engine = VaultAskEngine(repository, prompt, FakeAskPreferences())
        return AskViewModel(engine, prompt, dispatcherRule.dispatcher)
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
        val askViewModel = viewModel(repository, prompt)
        askViewModel.onEnteredAsk()
        advanceUntilIdle()

        askViewModel.submit("deployment")
        advanceUntilIdle()

        assertEquals("deployment", askViewModel.state.value.submittedQuestion)
        assertEquals("Answer [1].", askViewModel.state.value.askAnswer)
        assertEquals(1, askViewModel.state.value.askSources.size)
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
        val askViewModel = viewModel(repository, prompt)
        askViewModel.onEnteredAsk()
        askViewModel.submit("alpha")
        askViewModel.onLeftAsk()
        advanceUntilIdle()

        assertNull(askViewModel.state.value.askAnswer)
        assertFalse(askViewModel.state.value.askSubmitting)
    }
}
