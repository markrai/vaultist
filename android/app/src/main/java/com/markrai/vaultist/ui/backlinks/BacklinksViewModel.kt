package com.markrai.vaultist.ui.backlinks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.domain.Backlink
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BacklinksUiState(val loading: Boolean = true, val items: List<Backlink> = emptyList(), val error: String? = null)

@HiltViewModel
class BacklinksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
) : ViewModel() {
    val noteId: String = requireNotNull(savedStateHandle["id"])
    private val _state = MutableStateFlow(BacklinksUiState())
    val state: StateFlow<BacklinksUiState> = _state

    init { load() }
    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = BacklinksUiState(loading = true)
            when (val result = repository.getBacklinks(noteId)) {
                is VaultResult.Success -> _state.value = BacklinksUiState(loading = false, items = result.value)
                is VaultResult.Failure -> _state.value = BacklinksUiState(loading = false, error = result.error.userMessage())
            }
        }
    }
}
