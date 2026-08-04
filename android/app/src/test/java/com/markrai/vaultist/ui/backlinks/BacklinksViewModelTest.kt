package com.markrai.vaultist.ui.backlinks

import androidx.lifecycle.SavedStateHandle
import com.markrai.vaultist.domain.Backlink
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BacklinksViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test fun retainsMultipleOccurrencesFromOneSource() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            backlinksResult = VaultResult.Success(
                listOf(
                    Backlink("Source", "Source", "Source.md", 2, 1, "[[Target]]", null, null),
                    Backlink("Source", "Source", "Source.md", 8, 3, "[[Target|again]]", null, "again"),
                )
            )
        }
        val viewModel = BacklinksViewModel(SavedStateHandle(mapOf("id" to "Target")), repository)
        advanceUntilIdle()
        assertEquals(listOf(2, 8), viewModel.state.value.items.map { it.line })
    }
}
