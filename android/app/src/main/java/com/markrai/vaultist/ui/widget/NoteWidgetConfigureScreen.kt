package com.markrai.vaultist.ui.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markrai.vaultist.R
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.ui.theme.Spacing

@Composable
fun NoteWidgetConfigureScreen(
    state: NoteWidgetConfigureUiState,
    onQueryChange: (String) -> Unit,
    onSelectNote: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.note_widget_configure_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md),
        ) {
            when {
                !state.serverConfigured -> {
                    Text(
                        text = stringResource(R.string.note_widget_server_not_configured),
                        style = MaterialTheme.typography.body1,
                    )
                }
                else -> {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search notes") },
                        singleLine = true,
                    )
                    if (state.loading || state.searching) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = Spacing.md))
                    }
                    state.error?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colors.error,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = Spacing.sm),
                    ) {
                        items(state.items, key = { it.id ?: it.path }) { item ->
                            ConfigureNoteRow(
                                item = item,
                                selected = item.id == state.selectedNoteId,
                                onClick = { item.id?.let(onSelectNote) },
                            )
                        }
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = state.selectedNoteId != null && !state.binding,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.note_widget_configure_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureNoteRow(
    item: BrowseItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title = item.title?.takeIf { it.isNotBlank() } ?: item.name
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle1,
            color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.path,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NoteWidgetConfigureRoute(
    viewModel: NoteWidgetConfigureViewModel,
    onConfirm: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoteWidgetConfigureScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onSelectNote = viewModel::selectNote,
        onConfirm = onConfirm,
    )
}
