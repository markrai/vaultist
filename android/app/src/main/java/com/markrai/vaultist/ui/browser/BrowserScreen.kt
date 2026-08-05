package com.markrai.vaultist.ui.browser

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
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markrai.vaultist.R
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.ui.ask.AskHint
import com.markrai.vaultist.ui.ask.AskResultsPane
import com.markrai.vaultist.ui.ask.AskViewModel
import com.markrai.vaultist.ui.create.CreateNoteDialog
import com.markrai.vaultist.ui.create.CreateNoteViewModel
import com.markrai.vaultist.ui.components.ErrorPanel
import com.markrai.vaultist.ui.components.NoteResultCard
import com.markrai.vaultist.ui.theme.Spacing
import com.markrai.vaultist.ui.theme.VaultistThemeColors

private val ModeButtonWidth = 104.dp
private val SearchControlHeight = 56.dp

@Composable
fun BrowserScreen(
    onOpenNote: (String, Boolean) -> Unit,
    onSettings: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
    askViewModel: AskViewModel = hiltViewModel(),
    createNoteViewModel: CreateNoteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val askState by askViewModel.state.collectAsStateWithLifecycle()
    val createState by createNoteViewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(createState.pendingOpenNoteId) {
        createState.pendingOpenNoteId?.let { id ->
            createNoteViewModel.consumeOpenRequest()
            onOpenNote(id, true)
        }
    }

    val openNoteForBrowse: (String) -> Unit = { id -> onOpenNote(id, false) }

    DisposableEffect(lifecycleOwner, state.searchMode) {
        if (state.searchMode != SearchMode.Ask) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                askViewModel.onResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(state.vault?.name ?: stringResource(R.string.default_vault_name))
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
                if (state.vault?.readOnly == false) {
                    IconButton(onClick = createNoteViewModel::openDialog) {
                        Icon(Icons.Default.Add, "Create note")
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
                askSubmitting = askState.askSubmitting,
                onQueryChange = { query ->
                    viewModel.updateQuery(query)
                    if (state.searchMode == SearchMode.Ask && query.isBlank()) {
                        askViewModel.clearResults()
                    }
                },
                onClear = {
                    viewModel.updateQuery("")
                    if (state.searchMode == SearchMode.Ask) {
                        askViewModel.clearResults()
                    }
                },
                onToggleMode = {
                    if (state.searchMode == SearchMode.Ask) {
                        askViewModel.onLeftAsk()
                    }
                    viewModel.toggleSearchMode()
                    if (viewModel.state.value.searchMode == SearchMode.Ask) {
                        askViewModel.onEnteredAsk()
                    }
                },
                onSubmit = {
                    when (state.searchMode) {
                        SearchMode.Ask -> askViewModel.submit(state.query)
                        else -> viewModel.submitSearch()
                    }
                },
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
                    capability = askState.askCapability,
                    onRetry = askViewModel::recheckCapability,
                    onDownload = askViewModel::downloadAskModel,
                )
                SearchMode.Files -> Unit
            }
            if (state.searchMode == SearchMode.Ask) {
                AskResultsPane(
                    state = askState,
                    onOpenNote = openNoteForBrowse,
                    onCancel = askViewModel::cancel,
                    onRetry = { askViewModel.retry(state.query) },
                )
            } else {
                ResultsPane(
                    state = state,
                    onOpenFolder = { path ->
                        askViewModel.invalidate()
                        viewModel.openFolder(path)
                    },
                    onOpenNote = openNoteForBrowse,
                    onUp = viewModel::up,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
    }
    if (createState.dialogVisible) {
        CreateNoteDialog(
            folder = state.folder,
            title = createState.title,
            creating = createState.creating,
            error = createState.error,
            onTitleChange = createNoteViewModel::updateTitle,
            onDismiss = createNoteViewModel::dismissDialog,
            onCreate = { createNoteViewModel.submit(state.folder) },
        )
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
                backgroundColor = VaultistThemeColors.browseModeToggle,
                contentColor = VaultistThemeColors.onBrowseModeToggle,
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
