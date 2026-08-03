package com.vaultview.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultview.ui.components.ErrorPanel
import com.vaultview.ui.components.NoteResultCard
import com.vaultview.ui.theme.Spacing

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenNote: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Search notes") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = { Text("Filename, title, or alias") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::search, enabled = state.query.isNotBlank() && !state.loading) { Text("Search") }
            state.error?.let { ErrorPanel(it, onRetry = viewModel::search) }
            if (state.loading && state.results.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            if (state.searched && !state.loading && state.results.isEmpty() && state.error == null) Text("No matching notes.")
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.results, key = { it.id ?: it.path }) { item ->
                    NoteResultCard(item, onClick = { item.id?.let(onOpenNote) })
                }
                if (state.nextCursor != null) item { Button(onClick = viewModel::loadMore, enabled = !state.loading) { Text("Load more") } }
            }
        }
    }
}
