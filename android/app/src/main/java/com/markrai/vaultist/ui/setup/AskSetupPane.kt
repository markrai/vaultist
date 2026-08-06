package com.markrai.vaultist.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.markrai.vaultist.ui.theme.Spacing

@Composable
fun AskSetupPane(
    enableAskThinking: Boolean,
    onEnableAskThinkingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SettingsSwitchRow(
            title = "Extended thinking",
            subtitle = "Lets on-device Ask reason longer before answering. Slower; off by default.",
            checked = enableAskThinking,
            onCheckedChange = onEnableAskThinkingChange,
            modifier = Modifier.testTag("ask_thinking_row"),
            switchModifier = Modifier.testTag("ask_thinking_toggle"),
        )
    }
}
