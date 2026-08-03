package com.vaultview.ui.setup

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.vaultview.ui.theme.VaultViewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SetupScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun entersTestsAndGatesSavingAUrl() {
        var typed = ""
        var testClicks = 0
        composeRule.setContent {
            VaultViewTheme {
                SetupContent(
                    state = SetupUiState(url = typed, valid = false),
                    onUrlChange = { typed = it },
                    onTest = { testClicks++ },
                    onSave = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("server_url").performTextClearance()
        composeRule.onNodeWithTag("server_url").performTextInput("https://vega.example.ts.net")
        composeRule.onNodeWithTag("test_connection").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("save_server").assertIsNotEnabled()
        assertEquals(1, testClicks)
    }
}
