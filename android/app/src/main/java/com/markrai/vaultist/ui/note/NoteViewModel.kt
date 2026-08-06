package com.markrai.vaultist.ui.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.data.settings.DateTimeInsertFormatter
import com.markrai.vaultist.data.share.NoteSharePreparer
import com.markrai.vaultist.data.share.SharePayload
import com.markrai.vaultist.di.config.BrowseUiConfig
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.ui.browser.PendingBrowseSync
import com.markrai.vaultist.ui.note.edit.DraftTextEdit
import com.markrai.vaultist.ui.note.edit.NoteEditDraft
import com.markrai.vaultist.ui.note.edit.WikiLinkDraft
import com.markrai.vaultist.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteUiState(
    val loading: Boolean = true,
    val note: Note? = null,
    val error: String? = null,
    val canEdit: Boolean = false,
    val editing: Boolean = false,
    val draft: NoteEditDraft = NoteEditDraft("", 0, 0),
    val editorFocused: Boolean = false,
    val wikiSuggestions: List<BrowseItem> = emptyList(),
    val wikiSearching: Boolean = false,
    val baseRevision: String? = null,
    val saving: Boolean = false,
    val conflict: Boolean = false,
    val sharing: Boolean = false,
    val pendingShare: SharePayload? = null,
    val shareError: String? = null,
    val showDeleteDialog: Boolean = false,
    val deleting: Boolean = false,
    val noteDeleted: Boolean = false,
    /** Pixels into the first visible read block to restore when entering edit. */
    val editorPartialScrollOffsetPx: Int = 0,
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
    private val sharePreparer: NoteSharePreparer,
    noteOpenSeed: NoteOpenSeed,
    private val pendingBrowseSync: PendingBrowseSync,
    private val pendingNoteSync: PendingNoteSync,
    private val browseUiConfig: BrowseUiConfig,
    private val dateTimeInsertFormatter: DateTimeInsertFormatter,
) : ViewModel() {
    val noteId: String = requireNotNull(savedStateHandle["id"])
    val fragment: String? = savedStateHandle["fragment"]
    private val openInEdit: Boolean = savedStateHandle.get<String>("edit") == "true"
    private var autoEditPending = openInEdit
    private var reconcileJob: Job? = null
    private var wikiSearchJob: Job? = null
    private var readScrollAnchor = ReadScrollAnchor()
    private val _state = MutableStateFlow(NoteUiState())
    val state: StateFlow<NoteUiState> = _state

    init {
        val seeded = noteOpenSeed.consume(noteId)
        if (seeded != null) {
            applySeeded(seeded)
        } else {
            load()
        }
    }

    fun retry() = load(showLoading = true)
    fun assetUrl(id: String) = repository.assetUrl(id)

    fun onReturnedToScreen() {
        if (_state.value.editing) return
        pendingNoteSync.consumeReload(noteId)
        reconcileLinks()
    }

    fun onReadScrollChanged(sourceLine: Int, partialScrollOffsetPx: Int) {
        if (_state.value.editing) return
        readScrollAnchor = ReadScrollAnchor(
            sourceLine = sourceLine.coerceAtLeast(1),
            partialScrollOffsetPx = partialScrollOffsetPx.coerceAtLeast(0),
        )
    }

    fun enterEdit() {
        val note = _state.value.note ?: return
        val cursor = ReadScrollMapping.characterOffsetAtLine(note.content, readScrollAnchor.sourceLine)
        _state.update {
            it.copy(
                editing = true,
                draft = NoteEditDraft(note.content, cursor, cursor),
                editorPartialScrollOffsetPx = readScrollAnchor.partialScrollOffsetPx,
                editorFocused = false,
                wikiSuggestions = emptyList(),
                wikiSearching = false,
                baseRevision = note.revision,
                error = null,
                conflict = false,
            )
        }
    }

    fun cancelEdit() {
        wikiSearchJob?.cancel()
        _state.update {
            it.copy(
                editing = false,
                draft = NoteEditDraft("", 0, 0),
                editorFocused = false,
                wikiSuggestions = emptyList(),
                wikiSearching = false,
                baseRevision = null,
                saving = false,
                error = null,
                conflict = false,
            )
        }
    }

    fun updateDraft(draft: NoteEditDraft) {
        _state.update { it.copy(draft = draft) }
        refreshWikiSuggestions(draft)
    }

    fun onEditorFocusChanged(focused: Boolean) {
        _state.update { it.copy(editorFocused = focused) }
        if (!focused) {
            dismissWikiSuggestions()
        }
    }

    fun insertDateTime() {
        val current = _state.value
        if (!current.editing) return
        val stamp = dateTimeInsertFormatter.formatNow()
        updateDraft(DraftTextEdit.insertAtSelection(current.draft, stamp))
    }

    fun insertWikiLinkStart() {
        val current = _state.value
        if (!current.editing) return
        updateDraft(DraftTextEdit.insertAtSelection(current.draft, "[["))
    }

    fun applyWikiSuggestion(noteId: String) {
        val current = _state.value
        if (!current.editing) return
        val range = WikiLinkDraft.openRange(current.draft.text, current.draft.selectionStart) ?: return
        updateDraft(
            DraftTextEdit.replaceRange(current.draft, range, "[[$noteId]]"),
        )
    }

    fun dismissWikiSuggestions() {
        wikiSearchJob?.cancel()
        _state.update { it.copy(wikiSuggestions = emptyList(), wikiSearching = false) }
    }

    fun save() {
        val revision = _state.value.baseRevision
        if (revision.isNullOrBlank()) {
            _state.update { it.copy(error = "This note cannot be saved because its revision is missing. Cancel and reopen the note.") }
            return
        }
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, conflict = false) }
            when (val result = repository.updateNote(noteId, revision, _state.value.draft.text)) {
                is VaultResult.Success -> {
                    wikiSearchJob?.cancel()
                    _state.update {
                        it.copy(
                            saving = false,
                            editing = false,
                            draft = NoteEditDraft("", 0, 0),
                            editorFocused = false,
                            wikiSuggestions = emptyList(),
                            wikiSearching = false,
                            baseRevision = null,
                            note = result.value,
                            error = null,
                            conflict = false,
                        )
                    }
                    reconcileLinks()
                }
                is VaultResult.Failure -> {
                    val conflict = result.error is com.markrai.vaultist.domain.VaultError.Api &&
                        (result.error as com.markrai.vaultist.domain.VaultError.Api).code == "revision_conflict"
                    _state.update {
                        it.copy(
                            saving = false,
                            error = result.error.userMessage(),
                            conflict = conflict,
                        )
                    }
                }
            }
        }
    }

    fun reloadAfterConflict() {
        cancelEdit()
        load(showLoading = true)
    }

    fun share() {
        val note = _state.value.note ?: return
        if (_state.value.sharing) return
        viewModelScope.launch {
            _state.update { it.copy(sharing = true, shareError = null) }
            try {
                val content = if (_state.value.editing) _state.value.draft.text else note.content
                val payload = sharePreparer.prepare(
                    noteId = note.id,
                    filename = note.filename,
                    content = content,
                )
                _state.update { it.copy(sharing = false, pendingShare = payload) }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        sharing = false,
                        shareError = "Could not prepare this note for sharing.",
                    )
                }
            }
        }
    }

    fun consumeShareRequest() {
        _state.update { it.copy(pendingShare = null) }
    }

    fun clearShareError() {
        _state.update { it.copy(shareError = null) }
    }

    fun requestDelete() {
        if (!_state.value.canEdit || _state.value.note == null) return
        _state.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = false) }
    }

    fun confirmDelete() {
        val note = _state.value.note ?: return
        if (_state.value.deleting) return
        viewModelScope.launch {
            _state.update {
                it.copy(deleting = true, showDeleteDialog = false, error = null, conflict = false)
            }
            when (val result = repository.deleteNote(noteId, note.revision)) {
                is VaultResult.Success -> {
                    pendingBrowseSync.offerAfterDelete(noteId)
                    _state.update { it.copy(deleting = false, noteDeleted = true) }
                }
                is VaultResult.Failure -> {
                    val conflict = result.error is com.markrai.vaultist.domain.VaultError.Api &&
                        (result.error as com.markrai.vaultist.domain.VaultError.Api).code == "revision_conflict"
                    _state.update {
                        it.copy(
                            deleting = false,
                            error = result.error.userMessage(),
                            conflict = conflict,
                        )
                    }
                }
            }
        }
    }

    fun consumeNoteDeleted() {
        _state.update { it.copy(noteDeleted = false) }
    }

    private fun refreshWikiSuggestions(draft: NoteEditDraft) {
        val query = WikiLinkDraft.queryAtCursor(draft.text, draft.selectionStart)
        if (query.isNullOrBlank()) {
            dismissWikiSuggestions()
            return
        }
        wikiSearchJob?.cancel()
        wikiSearchJob = viewModelScope.launch {
            _state.update { it.copy(wikiSearching = true) }
            delay(browseUiConfig.debounceMs)
            when (val result = repository.searchNotes(query.trim(), SearchMode.Files)) {
                is VaultResult.Success -> {
                    val suggestions = result.value.items
                        .filter { it.kind == BrowseKind.Note }
                        .distinctBy { it.id }
                        .take(WIKI_SUGGESTION_LIMIT)
                    _state.update { it.copy(wikiSuggestions = suggestions, wikiSearching = false) }
                }
                is VaultResult.Failure -> {
                    _state.update { it.copy(wikiSuggestions = emptyList(), wikiSearching = false) }
                }
            }
        }
    }

    private fun applySeeded(note: Note) {
        viewModelScope.launch {
            val vault = repository.getVault()
            val canEdit = vault is VaultResult.Success && !vault.value.readOnly
            _state.update {
                it.copy(
                    loading = false,
                    note = note,
                    canEdit = canEdit,
                    error = null,
                )
            }
            if (autoEditPending && canEdit) {
                autoEditPending = false
                enterEdit()
            }
        }
    }

    private fun reconcileLinks() {
        val current = _state.value
        if (current.editing || current.loading || current.saving || current.deleting || current.note == null) return
        reconcileJob?.cancel()
        reconcileJob = viewModelScope.launch {
            repeat(browseUiConfig.indexPollAttempts) {
                val status = repository.getIndexStatus()
                if (status is VaultResult.Success && status.value.state != "indexing") {
                    load(showLoading = false)
                    return@launch
                }
                delay(browseUiConfig.indexPollDelayMs)
            }
        }
    }

    private fun load(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _state.update { it.copy(loading = true, error = null) }
            }
            val vault = repository.getVault()
            val canEdit = vault is VaultResult.Success && !vault.value.readOnly
            when (val result = repository.getNote(noteId)) {
                is VaultResult.Success -> {
                    val note = result.value
                    _state.update {
                        it.copy(
                            loading = false,
                            note = note,
                            canEdit = canEdit,
                            error = null,
                            baseRevision = if (it.editing) it.baseRevision else null,
                        )
                    }
                    if (autoEditPending && canEdit) {
                        autoEditPending = false
                        enterEdit()
                    }
                }
                is VaultResult.Failure -> {
                    if (showLoading) {
                        _state.update {
                            it.copy(loading = false, canEdit = canEdit, error = result.error.userMessage())
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val WIKI_SUGGESTION_LIMIT = 8
    }
}
