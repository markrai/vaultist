package com.markrai.vaultist.ui.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.data.share.NoteSharePreparer
import com.markrai.vaultist.data.share.SharePayload
import com.markrai.vaultist.di.config.BrowseUiConfig
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.ui.browser.PendingBrowseSync
import com.markrai.vaultist.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val draftContent: String = "",
    val baseRevision: String? = null,
    val saving: Boolean = false,
    val conflict: Boolean = false,
    val sharing: Boolean = false,
    val pendingShare: SharePayload? = null,
    val shareError: String? = null,
    val showDeleteDialog: Boolean = false,
    val deleting: Boolean = false,
    val noteDeleted: Boolean = false,
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
    private val sharePreparer: NoteSharePreparer,
    noteOpenSeed: NoteOpenSeed,
    private val pendingBrowseSync: PendingBrowseSync,
    private val browseUiConfig: BrowseUiConfig,
) : ViewModel() {
    val noteId: String = requireNotNull(savedStateHandle["id"])
    val fragment: String? = savedStateHandle["fragment"]
    private val openInEdit: Boolean = savedStateHandle.get<String>("edit") == "true"
    private var autoEditPending = openInEdit
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
        reconcileLinks()
    }

    fun enterEdit() {
        val note = _state.value.note ?: return
        _state.update {
            it.copy(
                editing = true,
                draftContent = note.content,
                baseRevision = note.revision,
                error = null,
                conflict = false,
            )
        }
    }

    fun cancelEdit() {
        _state.update {
            it.copy(
                editing = false,
                draftContent = "",
                baseRevision = null,
                saving = false,
                error = null,
                conflict = false,
            )
        }
    }

    fun updateDraft(content: String) {
        _state.update { it.copy(draftContent = content) }
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
            when (val result = repository.updateNote(noteId, revision, _state.value.draftContent)) {
                is VaultResult.Success -> {
                    _state.update {
                        it.copy(
                            saving = false,
                            editing = false,
                            draftContent = "",
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
                val content = if (_state.value.editing) _state.value.draftContent else note.content
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
        viewModelScope.launch {
            repeat(browseUiConfig.indexPollAttempts) {
                delay(browseUiConfig.indexPollDelayMs)
                val status = repository.getIndexStatus()
                if (status is VaultResult.Success && status.value.state != "indexing") {
                    load(showLoading = false)
                    return@launch
                }
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
}
