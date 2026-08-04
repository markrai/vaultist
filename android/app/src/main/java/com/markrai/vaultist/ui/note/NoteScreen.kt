package com.markrai.vaultist.ui.note

import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markrai.vaultist.domain.LinkCandidate
import com.markrai.vaultist.ui.components.ErrorPanel
import com.markrai.vaultist.ui.markdown.MarkdownRenderer

private data class AmbiguousDialog(val target: String, val candidates: List<LinkCandidate>)

@Composable
fun NoteScreen(
    onBack: () -> Unit,
    onOpenNote: (String, String?) -> Unit,
    onBacklinks: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    viewModel: NoteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var missingTarget by remember { mutableStateOf<String?>(null) }
    var ambiguous by remember { mutableStateOf<AmbiguousDialog?>(null) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(state.note?.title ?: "Note") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            actions = { IconButton(onClick = { onBacklinks(viewModel.noteId) }) { Icon(Icons.Default.Link, "Backlinks") } },
        )
    }) { padding ->
        when {
            state.loading -> androidx.compose.foundation.layout.Box(Modifier.padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> ErrorPanel(state.error.orEmpty(), Modifier.padding(padding), viewModel::retry)
            state.note != null -> MarkdownRenderer(
                note = requireNotNull(state.note),
                fragment = viewModel.fragment,
                assetUrl = viewModel::assetUrl,
                onOpenNote = onOpenNote,
                onMissing = { missingTarget = it },
                onAmbiguous = { target, candidates -> ambiguous = AmbiguousDialog(target, candidates) },
                onOpenImage = onOpenImage,
                modifier = Modifier.padding(padding),
            )
        }
    }
    missingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { missingTarget = null },
            title = { Text("Link target not found") },
            text = { Text(target) },
            confirmButton = { Button(onClick = { missingTarget = null }) { Text("OK") } },
        )
    }
    ambiguous?.let { dialog ->
        AlertDialog(
            onDismissRequest = { ambiguous = null },
            title = { Text("Choose a note") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("${dialog.target} matches more than one note:")
                    dialog.candidates.forEach { candidate ->
                        TextButton(onClick = { ambiguous = null; onOpenNote(candidate.id, null) }) { Text(candidate.path) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ambiguous = null }) { Text("Cancel") } },
        )
    }
}
