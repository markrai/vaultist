package com.vaultview.ui.setup

import com.vaultview.testutil.FakeAskPreferences
import com.vaultview.testutil.FakeVaultRepository
import com.vaultview.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test fun validatesTestsAndSavesOnlyTheTestedUrl() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository()
        val viewModel = SetupViewModel(repository, FakeAskPreferences())
        advanceUntilIdle()
        viewModel.updateUrl("https://vega.example.ts.net")
        viewModel.testConnection()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.valid)
        viewModel.save()
        advanceUntilIdle()
        assertEquals("https://vega.example.ts.net", repository.savedUrl)
        assertTrue(viewModel.state.value.saved)
    }

    @Test fun persistsAskThinkingPreference() = runTest(dispatcherRule.dispatcher) {
        val prefs = FakeAskPreferences()
        val viewModel = SetupViewModel(FakeVaultRepository(), prefs)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.enableAskThinking)
        viewModel.setEnableAskThinking(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.enableAskThinking)
    }
}
