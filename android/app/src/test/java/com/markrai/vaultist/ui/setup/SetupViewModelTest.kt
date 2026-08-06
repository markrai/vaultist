package com.markrai.vaultist.ui.setup

import com.markrai.vaultist.testutil.FakeAskPreferences
import com.markrai.vaultist.testutil.FakeDateTimeInsertPreferences
import com.markrai.vaultist.testutil.FakeModifiedDatePreferences
import com.markrai.vaultist.testutil.FakeThemePreferences
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.domain.DateTimeInsertFormat
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.testutil.MainDispatcherRule
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
        val viewModel = SetupViewModel(
            repository,
            FakeAskPreferences(),
            FakeThemePreferences(),
            FakeModifiedDatePreferences(),
            FakeDateTimeInsertPreferences(),
        )
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
        val viewModel = SetupViewModel(
            FakeVaultRepository(),
            prefs,
            FakeThemePreferences(),
            FakeModifiedDatePreferences(),
            FakeDateTimeInsertPreferences(),
        )
        advanceUntilIdle()
        assertFalse(viewModel.state.value.enableAskThinking)
        viewModel.setEnableAskThinking(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.enableAskThinking)
    }

    @Test fun persistsColorThemePreference() = runTest(dispatcherRule.dispatcher) {
        val themePrefs = FakeThemePreferences()
        val viewModel = SetupViewModel(
            FakeVaultRepository(),
            FakeAskPreferences(),
            themePrefs,
            FakeModifiedDatePreferences(),
            FakeDateTimeInsertPreferences(),
        )
        advanceUntilIdle()
        assertEquals(AppColorTheme.Ruby, viewModel.state.value.colorTheme)
        viewModel.setColorTheme(AppColorTheme.Forest)
        advanceUntilIdle()
        assertEquals(AppColorTheme.Forest, viewModel.state.value.colorTheme)
    }

    @Test fun persistsAppearancePreference() = runTest(dispatcherRule.dispatcher) {
        val themePrefs = FakeThemePreferences()
        val viewModel = SetupViewModel(
            FakeVaultRepository(),
            FakeAskPreferences(),
            themePrefs,
            FakeModifiedDatePreferences(),
            FakeDateTimeInsertPreferences(),
        )
        advanceUntilIdle()
        assertEquals(AppAppearance.Light, viewModel.state.value.appearance)
        viewModel.setAppearance(AppAppearance.Dark)
        advanceUntilIdle()
        assertEquals(AppAppearance.Dark, viewModel.state.value.appearance)
    }

    @Test fun persistsRelativeModifiedDatesPreference() = runTest(dispatcherRule.dispatcher) {
        val datePrefs = FakeModifiedDatePreferences()
        val viewModel = SetupViewModel(
            FakeVaultRepository(),
            FakeAskPreferences(),
            FakeThemePreferences(),
            datePrefs,
            FakeDateTimeInsertPreferences(),
        )
        advanceUntilIdle()
        assertFalse(viewModel.state.value.relativeModifiedDates)
        viewModel.setRelativeModifiedDates(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.relativeModifiedDates)
    }

    @Test fun persistsDateTimeInsertFormatPreference() = runTest(dispatcherRule.dispatcher) {
        val dateTimePrefs = FakeDateTimeInsertPreferences()
        val viewModel = SetupViewModel(
            FakeVaultRepository(),
            FakeAskPreferences(),
            FakeThemePreferences(),
            FakeModifiedDatePreferences(),
            dateTimePrefs,
        )
        advanceUntilIdle()
        assertEquals(DateTimeInsertFormat.IsoDateTime, viewModel.state.value.dateTimeInsertFormat)
        viewModel.setDateTimeInsertFormat(DateTimeInsertFormat.IsoDate)
        advanceUntilIdle()
        assertEquals(DateTimeInsertFormat.IsoDate, viewModel.state.value.dateTimeInsertFormat)
    }
}
