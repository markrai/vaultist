package com.markrai.vaultist.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.markrai.vaultist.R
import com.markrai.vaultist.ui.theme.Spacing

@Composable
fun ConnectSetupPane(
    state: SetupUiState,
    onUrlChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
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
        state.message?.let {
            Text(it, color = if (state.valid) MaterialTheme.colors.primary else MaterialTheme.colors.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = onTest, enabled = !state.testing, modifier = Modifier.testTag("test_connection")) {
                if (state.testing) CircularProgressIndicator(Modifier.padding(end = Spacing.sm))
                Text("Test connection")
            }
            Button(onClick = onSave, enabled = state.valid && !state.testing, modifier = Modifier.testTag("save_server")) {
                Text("Save")
            }
        }
        Text(stringResource(R.string.setup_url_storage_note), style = MaterialTheme.typography.caption)
    }
}
