package com.markrai.vaultist.ui.setup

import com.markrai.vaultist.testutil.FakeAskPreferences
import com.markrai.vaultist.testutil.FakeDateTimeInsertPreferences
import com.markrai.vaultist.testutil.FakeModifiedDatePreferences
import com.markrai.vaultist.testutil.FakeThemePreferences
import com.markrai.vaultist.testutil.FakeNoteWidgetRefresher
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.domain.DateTimeInsertFormat
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.HeadingColorPalette
import com.markrai.vaultist.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private fun viewModel(repository: FakeVaultRepository = FakeVaultRepository()) = SetupViewModel(
        repository,
        FakeAskPreferences(),
        FakeThemePreferences(),
        FakeModifiedDatePreferences(),
        FakeDateTimeInsertPreferences(),
        FakeNoteWidgetRefresher(),
    )

    @Test fun validatesTestsAndSavesOnlyTheTestedUrl() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository()
        val viewModel = viewModel(repository)
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
            FakeNoteWidgetRefresher(),
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
            FakeNoteWidgetRefresher(),
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
            FakeNoteWidgetRefresher(),
        )
        advanceUntilIdle()
        assertEquals(AppAppearance.Light, viewModel.state.value.appearance)
        viewModel.setAppearance(AppAppearance.Dark)
        advanceUntilIdle()
        assertEquals(AppAppearance.Dark, viewModel.state.value.appearance)
    }

    @Test fun persistsColorizedHeadingsPreference() = runTest(dispatcherRule.dispatcher) {
        val themePrefs = FakeThemePreferences()
        val widgetRefresh = FakeNoteWidgetRefresher()
        val viewModel = SetupViewModel(
            FakeVaultRepository(),
            FakeAskPreferences(),
            themePrefs,
            FakeModifiedDatePreferences(),
            FakeDateTimeInsertPreferences(),
            widgetRefresh,
        )
        advanceUntilIdle()
        assertFalse(viewModel.state.value.colorizedHeadings)
        viewModel.setColorizedHeadings(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.colorizedHeadings)
        assertEquals(1, widgetRefresh.refreshAllCalls)
    }

    @Test fun persistsHeadingColorPalettePreference() = runTest(dispatcherRule.dispatcher) {
        val themePrefs = FakeThemePreferences()
        val widgetRefresh = FakeNoteWidgetRefresher()
        val viewModel = SetupViewModel(
            FakeVaultRepository(),
            FakeAskPreferences(),
            themePrefs,
            FakeModifiedDatePreferences(),
            FakeDateTimeInsertPreferences(),
            widgetRefresh,
        )
        advanceUntilIdle()
        assertEquals(HeadingColorPalette.Classic, viewModel.state.value.headingColorPalette)
        viewModel.setHeadingColorPalette(HeadingColorPalette.Teal)
        advanceUntilIdle()
        assertEquals(HeadingColorPalette.Teal, viewModel.state.value.headingColorPalette)
        assertEquals(1, widgetRefresh.refreshAllCalls)
    }

    @Test fun persistsRelativeModifiedDatesPreference() = runTest(dispatcherRule.dispatcher) {
        val datePrefs = FakeModifiedDatePreferences()
        val viewModel = SetupViewModel(
            FakeVaultRepository(),
            FakeAskPreferences(),
            FakeThemePreferences(),
            datePrefs,
            FakeDateTimeInsertPreferences(),
            FakeNoteWidgetRefresher(),
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
            FakeNoteWidgetRefresher(),
        )
        advanceUntilIdle()
        assertEquals(DateTimeInsertFormat.IsoDateTime, viewModel.state.value.dateTimeInsertFormat)
        viewModel.setDateTimeInsertFormat(DateTimeInsertFormat.IsoDate)
        advanceUntilIdle()
        assertEquals(DateTimeInsertFormat.IsoDate, viewModel.state.value.dateTimeInsertFormat)
    }
}
