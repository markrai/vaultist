package com.markrai.vaultist.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.VaultError
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.domain.noteIdFromMissingLink
import com.markrai.vaultist.domain.noteIdFromTitle
import com.markrai.vaultist.ui.note.NoteOpenSeed
import com.markrai.vaultist.ui.note.PendingNoteSync
import com.markrai.vaultist.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CreateItemMode {
    Note,
    Folder,
}

data class CreateNoteUiState(
    val dialogVisible: Boolean = false,
    val mode: CreateItemMode = CreateItemMode.Note,
    val title: String = "",
    val creating: Boolean = false,
    val error: String? = null,
    val pendingOpenNote: Note? = null,
    val pendingCreatedFolder: BrowseItem? = null,
)

@HiltViewModel
class CreateNoteViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val noteOpenSeed: NoteOpenSeed,
    private val pendingNoteSync: PendingNoteSync,
) : ViewModel() {
    private val _state = MutableStateFlow(CreateNoteUiState())
    val state: StateFlow<CreateNoteUiState> = _state

    fun openDialog() {
        _state.update { CreateNoteUiState(dialogVisible = true) }
    }

    fun dismissDialog() {
        if (_state.value.creating) return
        _state.update { CreateNoteUiState() }
    }

    fun updateMode(mode: CreateItemMode) {
        _state.update { it.copy(mode = mode, error = null) }
    }

    fun updateTitle(title: String) {
        _state.update { it.copy(title = title, error = null) }
    }

    fun submit(folder: String) {
        if (_state.value.creating) return
        val trimmedTitle = _state.value.title.trim()
        val validation = noteIdFromTitle(folder, trimmedTitle)
        val path = validation.id
        if (path == null) {
            _state.update { it.copy(error = validation.error) }
            return
        }
        when (_state.value.mode) {
            CreateItemMode.Note -> submitNote(path, "")
            CreateItemMode.Folder -> submitFolder(path)
        }
    }

    private fun submitNote(id: String, content: String) {
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            when (val result = repository.createNote(id, content)) {
                is VaultResult.Success -> {
                    noteOpenSeed.offer(result.value)
                    _state.update { CreateNoteUiState(pendingOpenNote = result.value) }
                }
                is VaultResult.Failure -> _state.update {
                    it.copy(creating = false, error = result.error.userMessage())
                }
            }
        }
    }

    private fun submitFolder(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            when (val result = repository.createFolder(path)) {
                is VaultResult.Success -> {
                    _state.update { CreateNoteUiState(pendingCreatedFolder = result.value) }
                }
                is VaultResult.Failure -> _state.update {
                    it.copy(creating = false, error = result.error.userMessage())
                }
            }
        }
    }

    fun consumeOpenRequest() {
        _state.update { it.copy(pendingOpenNote = null) }
    }

    fun consumeCreatedFolderRequest() {
        _state.update { it.copy(pendingCreatedFolder = null) }
    }

    fun createMissingLink(sourceNoteId: String, target: String) {
        if (_state.value.creating) return
        val validation = noteIdFromMissingLink(sourceNoteId, target)
        val id = validation.id
        if (id == null) {
            _state.update { it.copy(error = validation.error) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            when (val result = repository.createNote(id, "")) {
                is VaultResult.Success -> {
                    pendingNoteSync.offerReload(sourceNoteId)
                    noteOpenSeed.offer(result.value)
                    _state.update { CreateNoteUiState(pendingOpenNote = result.value) }
                }
                is VaultResult.Failure -> {
                    val exists = result.error is VaultError.Api &&
                        (result.error as VaultError.Api).code == "note_exists"
                    if (exists) {
                        when (val existing = repository.getNote(id)) {
                            is VaultResult.Success -> {
                                pendingNoteSync.offerReload(sourceNoteId)
                                noteOpenSeed.offer(existing.value)
                                _state.update { CreateNoteUiState(pendingOpenNote = existing.value) }
                            }
                            is VaultResult.Failure -> _state.update {
                                it.copy(creating = false, error = result.error.userMessage())
                            }
                        }
                    } else {
                        _state.update {
                            it.copy(creating = false, error = result.error.userMessage())
                        }
                    }
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
