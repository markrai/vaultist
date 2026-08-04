package com.vaultview.ui.browser

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultview.data.ask.AskStage
import com.vaultview.data.genai.LocalAiCapability
import com.vaultview.domain.BrowseKind
import com.vaultview.domain.SearchMode
import com.vaultview.ui.components.ErrorPanel
import com.vaultview.ui.components.NoteResultCard
import com.vaultview.ui.theme.Spacing

private val ForestGreen = Color(0xFF2E5A3C)
private val ModeButtonWidth = 104.dp
private val SearchControlHeight = 56.dp

@Composable
fun BrowserScreen(
    onOpenFolder: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, state.searchMode) {
        if (state.searchMode != SearchMode.Ask) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAskResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(state.vault?.name ?: "Vault Peep")
                    if (!state.isSearchResults && state.searchMode != SearchMode.Ask && state.folder.isNotEmpty()) {
                        Text(state.folder, style = MaterialTheme.typography.caption)
                    }
                }
            },
            actions = {
                if (state.searchMode != SearchMode.Ask) {
                    IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                        Icon(Icons.Default.Refresh, "Refresh index")
                    }
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Server settings") }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchBar(
                query = state.query,
                searchMode = state.searchMode,
                askSubmitting = state.askSubmitting,
                onQueryChange = viewModel::updateQuery,
                onClear = { viewModel.updateQuery("") },
                onToggleMode = viewModel::toggleSearchMode,
                onSubmit = viewModel::submitSearch,
            )
            when (state.searchMode) {
                SearchMode.Content -> if (!state.isSearchResults) {
                    Text(
                        "Press Enter to search note content.",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.xs),
                    )
                }
                SearchMode.Ask -> AskHint(
                    capability = state.askCapability,
                    onRetry = viewModel::recheckCapability,
                    onDownload = viewModel::downloadAskModel,
                )
                SearchMode.Files -> Unit
            }
            if (state.searchMode == SearchMode.Ask) {
                AskResultsPane(
                    state = state,
                    onOpenNote = onOpenNote,
                    onCancel = viewModel::cancelAsk,
                    onRetry = viewModel::retry,
                )
            } else {
                ResultsPane(
                    state = state,
                    onOpenFolder = { path ->
                        viewModel.openFolder(path)
                        onOpenFolder(path)
                    },
                    onOpenNote = onOpenNote,
                    onUp = viewModel::up,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
    }
}

@Composable
private fun AskHint(
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
private fun SearchBar(
    query: String,
    searchMode: SearchMode,
    askSubmitting: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = when (searchMode) {
                        SearchMode.Files -> "Filename, title, or alias"
                        SearchMode.Content -> "Note body text"
                        SearchMode.Ask -> "Ask a question"
                    },
                    style = MaterialTheme.typography.caption,
                )
            },
            singleLine = true,
            enabled = !askSubmitting,
            modifier = Modifier.weight(1f).height(SearchControlHeight),
            trailingIcon = {
                if (query.isNotEmpty() && !askSubmitting) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = when (searchMode) {
                    SearchMode.Files -> ImeAction.Default
                    SearchMode.Content, SearchMode.Ask -> ImeAction.Search
                },
            ),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        )
        Button(
            onClick = onToggleMode,
            enabled = !askSubmitting,
            modifier = Modifier.width(ModeButtonWidth).height(SearchControlHeight),
            elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            contentPadding = PaddingValues(horizontal = Spacing.xs),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ForestGreen,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = when (searchMode) {
                    SearchMode.Files -> "Files"
                    SearchMode.Content -> "Content"
                    SearchMode.Ask -> "Ask"
                },
                style = MaterialTheme.typography.caption,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AskResultsPane(
    state: BrowserUiState,
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

@Composable
private fun ResultsPane(
    state: BrowserUiState,
    onOpenFolder: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onUp: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    when {
        state.loading && state.items.isEmpty() -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        state.error != null && state.items.isEmpty() && !state.searching -> ErrorPanel(
            state.error.orEmpty(),
            Modifier.padding(Spacing.md),
            onRetry,
        )
        else -> LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.searching) {
                item {
                    Box(Modifier.fillMaxWidth().padding(Spacing.sm), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (!state.isSearchResults && state.folder.isNotEmpty()) item {
                Text(
                    "..",
                    Modifier.fillMaxWidth().clickable(onClick = onUp).padding(vertical = Spacing.md),
                    color = MaterialTheme.colors.primary,
                )
            }
            items(state.items, key = { "${it.kind}:${it.path}" }) { item ->
                if (item.kind == BrowseKind.Folder) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenFolder(item.path) }.padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colors.primary)
                        Text(item.name)
                    }
                } else {
                    NoteResultCard(item, onClick = { item.id?.let(onOpenNote) })
                }
            }
            if (state.items.isEmpty() && !state.searching && !state.loading) {
                item {
                    Text(
                        when {
                            state.isSearchResults && state.searched -> "No matching notes."
                            else -> "This folder has no Markdown notes."
                        },
                        Modifier.padding(vertical = Spacing.lg),
                    )
                }
            }
            state.error?.let { item { ErrorPanel(it) } }
            if (state.nextCursor != null) {
                item {
                    Button(onClick = onLoadMore, enabled = !state.loadingMore) {
                        Text(if (state.loadingMore) "Loading…" else "Load more")
                    }
                }
            }
        }
    }
}
