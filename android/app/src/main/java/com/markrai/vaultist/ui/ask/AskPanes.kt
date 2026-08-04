package com.markrai.vaultist.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.markrai.vaultist.data.ask.AskStage
import com.markrai.vaultist.data.genai.LocalAiCapability
import com.markrai.vaultist.ui.components.ErrorPanel
import com.markrai.vaultist.ui.components.NoteResultCard
import com.markrai.vaultist.ui.theme.Spacing

@Composable
fun AskHint(
    capability: LocalAiCapability,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.xs)) {
        Text(
            "Press Enter to ask. Vault search runs on the Linux host; answering is on-device.",
            style = MaterialTheme.typography.caption,
        )
        when (capability) {
            LocalAiCapability.Checking -> Row(
                Modifier.padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.height(14.dp).width(14.dp),
                    strokeWidth = 2.dp,
                )
                Text("Checking on-device AI…", style = MaterialTheme.typography.caption)
            }
            LocalAiCapability.Downloadable -> Column(
                Modifier.padding(top = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text("On-device AI needs a one-time download.", style = MaterialTheme.typography.caption)
                OutlinedButton(onClick = onDownload, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
                    Text("Download", style = MaterialTheme.typography.caption)
                }
            }
            is LocalAiCapability.Downloading -> Row(
                Modifier.padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.height(14.dp).width(14.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    "Setting up on-device AI… (one-time download)",
                    style = MaterialTheme.typography.caption,
                )
            }
            is LocalAiCapability.Failed -> Column(
                Modifier.padding(top = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    if (capability.retryable) capability.reason else "On-device AI is currently unavailable.",
                    style = MaterialTheme.typography.caption,
                )
                OutlinedButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
                    Text("Retry", style = MaterialTheme.typography.caption)
                }
            }
            LocalAiCapability.Unavailable,
            LocalAiCapability.Unchecked,
            -> Column(
                Modifier.padding(top = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text("On-device AI is currently unavailable.", style = MaterialTheme.typography.caption)
                OutlinedButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
                    Text("Retry", style = MaterialTheme.typography.caption)
                }
            }
            is LocalAiCapability.Ready -> Unit
        }
    }
}

@Composable
fun AskResultsPane(
    state: AskUiState,
    onOpenNote: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.askSubmitting) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        when (state.askStage) {
                            AskStage.CheckingOnDeviceAi -> "Checking on-device AI…"
                            AskStage.SearchingHost -> "Searching vault on the Linux host…"
                            AskStage.LoadingNotes -> "Loading matching notes…"
                            AskStage.AnsweringOnDevice -> "Answering on device…"
                            null -> "Starting Ask…"
                        },
                        style = MaterialTheme.typography.body2,
                    )
                    Text(
                        when (state.askStage) {
                            AskStage.CheckingOnDeviceAi -> "Making sure Gemini Nano is ready"
                            AskStage.SearchingHost -> "Running Files + Content search over the network"
                            AskStage.LoadingNotes -> "Fetching note text for evidence"
                            AskStage.AnsweringOnDevice -> "Generating a cited answer on this phone"
                            null -> "Preparing your question"
                        },
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    )
                    state.submittedQuestion?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(Spacing.sm), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        state.submittedQuestion?.let { question ->
            if (!state.askSubmitting) {
                item {
                    Text("Question", style = MaterialTheme.typography.overline)
                    Text(question, style = MaterialTheme.typography.body1, modifier = Modifier.padding(bottom = Spacing.sm))
                }
            }
        }

        state.askAnswer?.let { answer ->
            item {
                Text("Answer", style = MaterialTheme.typography.overline)
                Text(answer, style = MaterialTheme.typography.body1, modifier = Modifier.padding(bottom = Spacing.sm))
                if (state.askHadInvalidCitations) {
                    Text(
                        "Some citation markers were removed because they did not match sources.",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
        }

        state.askMessage?.let { message ->
            item {
                Text(message, style = MaterialTheme.typography.body2, modifier = Modifier.padding(vertical = Spacing.sm))
            }
        }

        if (state.askSources.isNotEmpty()) {
            item { Text("Sources", style = MaterialTheme.typography.overline) }
            items(state.askSources, key = { "ask:${it.path}" }) { item ->
                NoteResultCard(item, onClick = { item.id?.let(onOpenNote) })
            }
        }

        if (!state.askSubmitting && state.submittedQuestion != null &&
            state.askAnswer == null && state.askSources.isEmpty() && state.askMessage == null && state.error == null
        ) {
            item { Text("No matching notes.", Modifier.padding(vertical = Spacing.lg)) }
        }

        state.error?.let { message ->
            item { ErrorPanel(message, onRetry = onRetry) }
        }
    }
}
