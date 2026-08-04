package com.vaultview.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultview.ui.theme.Spacing

@Composable
fun SetupScreen(onSaved: () -> Unit, onBack: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    SetupContent(
        state = state,
        onUrlChange = viewModel::updateUrl,
        onTest = viewModel::testConnection,
        onSave = viewModel::save,
        onEnableAskThinkingChange = viewModel::setEnableAskThinking,
        onBack = onBack,
    )
}

@Composable
fun SetupContent(
    state: SetupUiState,
    onUrlChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onEnableAskThinkingChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Vault Peep server") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Enter the HTTPS address exposed by Tailscale Serve. The Android emulator development default is 10.0.2.2, not localhost.")
            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                label = { Text("Server URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onTest() }),
                modifier = Modifier.fillMaxWidth().testTag("server_url"),
            )
            state.message?.let { Text(it, color = if (state.valid) MaterialTheme.colors.primary else MaterialTheme.colors.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onTest, enabled = !state.testing, modifier = Modifier.testTag("test_connection")) {
                    if (state.testing) CircularProgressIndicator(Modifier.padding(end = Spacing.sm))
                    Text("Test connection")
                }
                Button(onClick = onSave, enabled = state.valid && !state.testing, modifier = Modifier.testTag("save_server")) { Text("Save") }
            }
            Text("Vault Peep stores only this URL. It does not store SMB credentials or request storage access.", style = MaterialTheme.typography.caption)

            Text("Ask", style = MaterialTheme.typography.subtitle1)
            Row(
                Modifier.fillMaxWidth().testTag("ask_thinking_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
                    Text("Extended thinking")
                    Text(
                        "Lets on-device Ask reason longer before answering. Slower; off by default.",
                        style = MaterialTheme.typography.caption,
                    )
                }
                Switch(
                    checked = state.enableAskThinking,
                    onCheckedChange = onEnableAskThinkingChange,
                    modifier = Modifier.testTag("ask_thinking_toggle"),
                )
            }
        }
    }
}
