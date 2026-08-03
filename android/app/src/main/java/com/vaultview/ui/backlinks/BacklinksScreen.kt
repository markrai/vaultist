package com.vaultview.ui.backlinks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultview.ui.components.ErrorPanel
import com.vaultview.ui.theme.Spacing

@Composable
fun BacklinksScreen(
    onBack: () -> Unit,
    onOpenSource: (String, Int) -> Unit,
    viewModel: BacklinksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Backlinks") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
            state.error != null -> ErrorPanel(state.error.orEmpty(), Modifier.padding(padding), viewModel::retry)
            state.items.isEmpty() -> Text("No notes link here yet.", Modifier.padding(padding).padding(Spacing.md))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.items, key = { "${it.sourceId}:${it.line}:${it.column}" }) { backlink ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenSource(backlink.sourceId, backlink.line) }) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(backlink.sourceTitle, style = MaterialTheme.typography.subtitle1)
                            Text(backlink.sourcePath, style = MaterialTheme.typography.caption)
                            Text(backlink.context, style = MaterialTheme.typography.body2)
                            Text("Line ${backlink.line}", color = MaterialTheme.colors.primary, style = MaterialTheme.typography.caption)
                        }
                    }
                }
            }
        }
    }
}
