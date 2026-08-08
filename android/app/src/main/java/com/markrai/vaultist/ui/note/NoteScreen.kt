package com.markrai.vaultist.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markrai.vaultist.domain.LinkCandidate
import com.markrai.vaultist.ui.components.ErrorPanel
import com.markrai.vaultist.ui.create.CreateNoteViewModel
import com.markrai.vaultist.ui.markdown.MarkdownRenderer
import com.markrai.vaultist.ui.note.edit.NoteEditor
import com.markrai.vaultist.ui.share.ShareNoteEffect
import com.markrai.vaultist.ui.theme.Spacing

private data class AmbiguousDialog(val target: String, val candidates: List<LinkCandidate>)
private data class MissingLinkDialog(val target: String, val isAsset: Boolean)

@Composable
fun NoteScreen(
    onBack: () -> Unit,
    onOpenNote: (String, String?) -> Unit,
    onOpenNoteForEdit: (String) -> Unit,
    onBacklinks: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onDeleted: () -> Unit = onBack,
    viewModel: NoteViewModel = hiltViewModel(),
    createNoteViewModel: CreateNoteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createState by createNoteViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var missingLink by remember { mutableStateOf<MissingLinkDialog?>(null) }
    var ambiguous by remember { mutableStateOf<AmbiguousDialog?>(null) }
    LaunchedEffect(state.noteDeleted) {
        if (state.noteDeleted) {
            viewModel.consumeNoteDeleted()
            onDeleted()
        }
    }
    LaunchedEffect(createState.pendingOpenNote) {
        createState.pendingOpenNote?.let { note ->
            createNoteViewModel.consumeOpenRequest()
            missingLink = null
            createNoteViewModel.clearError()
            onOpenNoteForEdit(note.id)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasBeenVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (hasBeenVisible) {
            viewModel.onReturnedToScreen()
        } else {
            hasBeenVisible = true
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasBeenVisible) {
                viewModel.onReturnedToScreen()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    ShareNoteEffect(
        pendingShare = state.pendingShare,
        shareError = state.shareError,
        onConsumed = viewModel::consumeShareRequest,
        onClearError = viewModel::clearShareError,
        snackbarHostState = snackbarHostState,
    )
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.background),
            ) {
                Text(
                    text = state.note?.title ?: "Note",
                    style = MaterialTheme.typography.h6,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.md,
                            end = Spacing.md,
                            top = Spacing.sm,
                            bottom = Spacing.xs,
                        ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TopAppBar(
                    title = {},
                    backgroundColor = MaterialTheme.colors.background,
                    contentColor = MaterialTheme.colors.onBackground,
                    elevation = 0.dp,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        when {
                            state.editing -> {
                                TextButton(onClick = viewModel::cancelEdit, enabled = !state.saving) {
                                    Text("Cancel")
                                }
                                TextButton(onClick = { viewModel.save() }, enabled = !state.saving) {
                                    Text("Save")
                                }
                            }
                            state.canEdit && state.note != null -> {
                                IconButton(onClick = viewModel::enterEdit) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                            }
                        }
                        if (!state.editing) {
                            IconButton(onClick = { onBacklinks(viewModel.noteId) }) {
                                Icon(Icons.Default.Link, contentDescription = "Backlinks")
                            }
                            IconButton(
                                onClick = viewModel::share,
                                enabled = state.note != null && !state.loading && !state.sharing,
                            ) {
                                Icon(Icons.Default.ArrowCircleRight, contentDescription = "Share note")
                            }
                            if (state.canEdit) {
                                IconButton(
                                    onClick = viewModel::requestDelete,
                                    enabled = state.note != null && !state.loading && !state.deleting &&
                                        !state.saving && !state.sharing,
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete note")
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        when {
            state.editing -> NoteEditor(
                draft = state.draft,
                editorFocused = state.editorFocused,
                wikiSuggestions = state.wikiSuggestions,
                wikiSearching = state.wikiSearching,
                saving = state.saving,
                error = state.error?.takeIf { !state.conflict },
                initialPartialScrollOffsetPx = state.editorPartialScrollOffsetPx,
                onDraftChange = viewModel::updateDraft,
                onEditorFocusChanged = viewModel::onEditorFocusChanged,
                onInsertCheckbox = viewModel::insertCheckbox,
                onInsertDateTime = viewModel::insertDateTime,
                onInsertWikiLink = viewModel::insertWikiLinkStart,
                onWikiSuggestionSelected = viewModel::applyWikiSuggestion,
                onRetrySave = viewModel::save,
                modifier = Modifier.padding(padding),
            )
            state.loading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null && state.note == null -> ErrorPanel(
                state.error.orEmpty(),
                Modifier.padding(padding),
                viewModel::retry,
            )
            state.note != null -> MarkdownRenderer(
                note = requireNotNull(state.note),
                fragment = viewModel.fragment,
                assetUrl = viewModel::assetUrl,
                onOpenNote = onOpenNote,
                onMissing = { target, isAsset -> missingLink = MissingLinkDialog(target, isAsset) },
                onAmbiguous = { target, candidates -> ambiguous = AmbiguousDialog(target, candidates) },
                onOpenImage = onOpenImage,
                onReadScrollChanged = viewModel::onReadScrollChanged,
                canToggleTasks = state.canEdit && !state.editing,
                onTaskToggle = viewModel::toggleTask,
                taskToggleInFlightLine = state.taskToggleSourceLine,
                modifier = Modifier.padding(padding),
            )
        }
        if ((state.saving && state.editing) || state.deleting) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }
    }
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = { Text("Delete note?") },
            text = {
                Text(
                    state.note?.let { "${it.title}\n${it.path}" }
                        ?: "This note will be permanently deleted from the vault.",
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) { Text("Cancel") }
            },
        )
    }
    if (state.conflict) {
        AlertDialog(
            onDismissRequest = viewModel::reloadAfterConflict,
            title = { Text("Note changed on server") },
            text = { Text(state.error.orEmpty()) },
            confirmButton = { Button(onClick = viewModel::reloadAfterConflict) { Text("Reload") } },
        )
    }
    missingLink?.let { dialog ->
        val canOfferCreate = !dialog.isAsset && state.canEdit
        AlertDialog(
            onDismissRequest = {
                if (!createState.creating) {
                    missingLink = null
                    createNoteViewModel.clearError()
                }
            },
            title = { Text("Link target not found") },
            text = {
                Column {
                    Text(dialog.target)
                    if (canOfferCreate) {
                        Text("Create note: ${dialog.target}")
                    }
                    createState.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            },
            confirmButton = {
                if (canOfferCreate) {
                    Button(
                        onClick = { createNoteViewModel.createMissingLink(viewModel.noteId, dialog.target) },
                        enabled = !createState.creating,
                    ) {
                        Text(if (createState.creating) "Creating…" else "Yes")
                    }
                } else {
                    Button(onClick = { missingLink = null }) { Text("OK") }
                }
            },
            dismissButton = {
                if (canOfferCreate) {
                    TextButton(
                        onClick = {
                            missingLink = null
                            createNoteViewModel.clearError()
                        },
                        enabled = !createState.creating,
                    ) { Text("No") }
                }
            },
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
                        TextButton(onClick = { ambiguous = null; onOpenNote(candidate.id, null) }) {
                            Text(candidate.path)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ambiguous = null }) { Text("Cancel") } },
        )
    }
}
