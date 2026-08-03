package com.vaultview.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultview.domain.BrowseKind
import com.vaultview.ui.components.ErrorPanel
import com.vaultview.ui.components.NoteResultCard
import com.vaultview.ui.theme.Spacing

@Composable
fun BrowserScreen(
    onOpenFolder: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text(state.vault?.name ?: "Vault Peep"); if (state.folder.isNotEmpty()) Text(state.folder, style = MaterialTheme.typography.caption) } },
            actions = {
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
                IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) { Icon(Icons.Default.Refresh, "Refresh index") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Server settings") }
            },
        )
    }) { padding ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            state.error != null && state.items.isEmpty() -> ErrorPanel(state.error.orEmpty(), Modifier.padding(padding), viewModel::retry)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (state.folder.isNotEmpty()) item {
                    Text("..", Modifier.fillMaxWidth().clickable { viewModel.up() }.padding(vertical = Spacing.md), color = MaterialTheme.colors.primary)
                }
                items(state.items, key = { "${it.kind}:${it.path}" }) { item ->
                    if (item.kind == BrowseKind.Folder) {
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.openFolder(item.path); onOpenFolder(item.path) }.padding(vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colors.primary); Text(item.name) }
                    } else {
                        NoteResultCard(item, onClick = { item.id?.let(onOpenNote) })
                    }
                }
                if (state.items.isEmpty()) item { Text("This folder has no Markdown notes.", Modifier.padding(vertical = Spacing.lg)) }
                state.error?.let { item { ErrorPanel(it) } }
                if (state.nextCursor != null) item { Button(onClick = viewModel::loadMore, enabled = !state.loadingMore) { Text(if (state.loadingMore) "Loading…" else "Load more") } }
            }
        }
    }
}
