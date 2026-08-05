package com.markrai.vaultist.ui.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.data.share.NoteSharePreparer
import com.markrai.vaultist.data.share.SharePayload
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
    private val sharePreparer: NoteSharePreparer,
) : ViewModel() {
    val noteId: String = requireNotNull(savedStateHandle["id"])
    val fragment: String? = savedStateHandle["fragment"]
    private val _state = MutableStateFlow(NoteUiState())
    val state: StateFlow<NoteUiState> = _state

    init {
        load()
    }

    fun retry() = load()
    fun assetUrl(id: String) = repository.assetUrl(id)

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
        load()
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

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val vault = repository.getVault()
            val canEdit = vault is VaultResult.Success && !vault.value.readOnly
            when (val result = repository.getNote(noteId)) {
                is VaultResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        note = result.value,
                        canEdit = canEdit,
                        error = null,
                        baseRevision = if (it.editing) it.baseRevision else null,
                    )
                }
                is VaultResult.Failure -> _state.update {
                    it.copy(loading = false, canEdit = canEdit, error = result.error.userMessage())
                }
            }
        }
    }
}
