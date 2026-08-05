package com.markrai.vaultist.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.domain.noteIdFromTitle
import com.markrai.vaultist.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateNoteUiState(
    val dialogVisible: Boolean = false,
    val title: String = "",
    val creating: Boolean = false,
    val error: String? = null,
    val pendingOpenNoteId: String? = null,
)

@HiltViewModel
class CreateNoteViewModel @Inject constructor(
    private val repository: VaultRepository,
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

    fun updateTitle(title: String) {
        _state.update { it.copy(title = title, error = null) }
    }

    fun submit(folder: String) {
        if (_state.value.creating) return
        val trimmedTitle = _state.value.title.trim()
        val validation = noteIdFromTitle(folder, trimmedTitle)
        val id = validation.id
        if (id == null) {
            _state.update { it.copy(error = validation.error) }
            return
        }
        val content = "# $trimmedTitle\n\n"
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            when (val result = repository.createNote(id, content)) {
                is VaultResult.Success -> _state.update { CreateNoteUiState(pendingOpenNoteId = id) }
                is VaultResult.Failure -> _state.update {
                    it.copy(creating = false, error = result.error.userMessage())
                }
            }
        }
    }

    fun consumeOpenRequest() {
        _state.update { it.copy(pendingOpenNoteId = null) }
    }
}
