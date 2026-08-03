package com.vaultview.ui.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultview.data.repository.VaultRepository
import com.vaultview.domain.Note
import com.vaultview.domain.VaultResult
import com.vaultview.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteUiState(val loading: Boolean = true, val note: Note? = null, val error: String? = null)

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
) : ViewModel() {
    val noteId: String = requireNotNull(savedStateHandle["id"])
    val fragment: String? = savedStateHandle["fragment"]
    private val _state = MutableStateFlow(NoteUiState())
    val state: StateFlow<NoteUiState> = _state

    init { load() }
    fun retry() = load()
    fun assetUrl(id: String) = repository.assetUrl(id)

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.getNote(noteId)) {
                is VaultResult.Success -> _state.value = NoteUiState(loading = false, note = result.value)
                is VaultResult.Failure -> _state.value = NoteUiState(loading = false, error = result.error.userMessage())
            }
        }
    }
}
