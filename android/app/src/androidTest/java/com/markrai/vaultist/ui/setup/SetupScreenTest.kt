package com.markrai.vaultist.ui.setup

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.markrai.vaultist.ui.theme.VaultistTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SetupScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun entersTestsAndGatesSavingAUrl() {
        var typed = ""
        var testClicks = 0
        composeRule.setContent {
            VaultistTheme {
                SetupContent(
                    state = SetupUiState(url = typed, valid = false),
                    onUrlChange = { typed = it },
                    onTest = { testClicks++ },
                    onSave = {},
                    onEnableAskThinkingChange = {},
                    onColorThemeChange = {},
                    onAppearanceChange = {},
                    onColorizedHeadingsChange = {},
                    onHeadingColorPaletteChange = {},
                    onRelativeModifiedDatesChange = {},
                    onDateTimeInsertFormatChange = {},
                    onBack = {},
                    initialTab = SetupTab.CONNECT,
                )
            }
        }
        composeRule.onNodeWithTag("server_url").performTextClearance()
        composeRule.onNodeWithTag("server_url").performTextInput("https://vega.example.ts.net")
        composeRule.onNodeWithTag("test_connection").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("save_server").assertIsNotEnabled()
        assertEquals(1, testClicks)
    }

    @Test fun showsPreferencesByDefault() {
        composeRule.setContent {
            VaultistTheme {
                SetupContent(
                    state = SetupUiState(),
                    onUrlChange = {},
                    onTest = {},
                    onSave = {},
                    onEnableAskThinkingChange = {},
                    onColorThemeChange = {},
                    onAppearanceChange = {},
                    onColorizedHeadingsChange = {},
                    onHeadingColorPaletteChange = {},
                    onRelativeModifiedDatesChange = {},
                    onDateTimeInsertFormatChange = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("relative_modified_dates_toggle").assertExists()
        composeRule.onNodeWithTag("datetime_format_iso_date_time").assertExists()
        composeRule.onNodeWithTag("ask_thinking_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("server_url").assertDoesNotExist()
    }

    @Test fun askControlsVisibleOnAskTab() {
        composeRule.setContent {
            VaultistTheme {
                SetupContent(
                    state = SetupUiState(),
                    onUrlChange = {},
                    onTest = {},
                    onSave = {},
                    onEnableAskThinkingChange = {},
                    onColorThemeChange = {},
                    onAppearanceChange = {},
                    onColorizedHeadingsChange = {},
                    onHeadingColorPaletteChange = {},
                    onRelativeModifiedDatesChange = {},
                    onDateTimeInsertFormatChange = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("setup_tab_ask").performClick()
        composeRule.onNodeWithTag("ask_thinking_toggle").assertExists()
    }

    @Test fun themeControlsVisibleOnThemeTab() {
        composeRule.setContent {
            VaultistTheme {
                SetupContent(
                    state = SetupUiState(),
                    onUrlChange = {},
                    onTest = {},
                    onSave = {},
                    onEnableAskThinkingChange = {},
                    onColorThemeChange = {},
                    onAppearanceChange = {},
                    onColorizedHeadingsChange = {},
                    onHeadingColorPaletteChange = {},
                    onRelativeModifiedDatesChange = {},
                    onDateTimeInsertFormatChange = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("setup_tab_theme").performClick()
        composeRule.onNodeWithTag("theme_ruby").assertExists()
        composeRule.onNodeWithTag("appearance_dark").assertExists()
        composeRule.onNodeWithTag("heading_palette_classic").assertExists()
        composeRule.onNodeWithTag("heading_palette_classic_reversed").assertExists()
        composeRule.onNodeWithTag("heading_palette_teal").assertExists()
        composeRule.onNodeWithTag("heading_palette_teal_reversed").assertExists()
    }
}
