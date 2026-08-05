package com.markrai.vaultist.ui.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
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
    val saving: Boolean = false,
    val conflict: Boolean = false,
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
) : ViewModel() {
    val noteId: String = requireNotNull(savedStateHandle["id"])
    val fragment: String? = savedStateHandle["fragment"]
    private val _state = MutableStateFlow(NoteUiState())
    val state: StateFlow<NoteUiState> = _state
    private var baseRevision: String? = null

    init {
        load()
    }

    fun retry() = load()
    fun assetUrl(id: String) = repository.assetUrl(id)

    fun enterEdit() {
        val note = _state.value.note ?: return
        baseRevision = note.revision
        _state.update {
            it.copy(editing = true, draftContent = note.content, error = null, conflict = false)
        }
    }

    fun cancelEdit() {
        _state.update {
            it.copy(editing = false, draftContent = "", saving = false, error = null, conflict = false)
        }
        baseRevision = null
    }

    fun updateDraft(content: String) {
        _state.update { it.copy(draftContent = content) }
    }

    fun save() {
        val revision = baseRevision ?: return
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, conflict = false) }
            when (val result = repository.updateNote(noteId, revision, _state.value.draftContent)) {
                is VaultResult.Success -> {
                    baseRevision = null
                    _state.update {
                        it.copy(
                            saving = false,
                            editing = false,
                            draftContent = "",
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

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val vault = repository.getVault()
            val canEdit = vault is VaultResult.Success && !vault.value.readOnly
            when (val result = repository.getNote(noteId)) {
                is VaultResult.Success -> _state.update {
                    it.copy(loading = false, note = result.value, canEdit = canEdit, error = null)
                }
                is VaultResult.Failure -> _state.update {
                    it.copy(loading = false, canEdit = canEdit, error = result.error.userMessage())
                }
            }
        }
    }
}
