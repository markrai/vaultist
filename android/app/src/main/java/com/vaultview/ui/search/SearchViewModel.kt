package com.vaultview.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultview.data.repository.VaultRepository
import com.vaultview.domain.BrowseItem
import com.vaultview.domain.VaultResult
import com.vaultview.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<BrowseItem> = emptyList(),
    val nextCursor: String? = null,
    val error: String? = null,
    val searched: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: VaultRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    fun updateQuery(query: String) = _state.update { it.copy(query = query, error = null) }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty() || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, results = emptyList(), error = null, searched = true) }
            when (val result = repository.searchNotes(query)) {
                is VaultResult.Success -> _state.update {
                    it.copy(loading = false, results = result.value.items, nextCursor = result.value.nextCursor)
                }
                is VaultResult.Failure -> _state.update { it.copy(loading = false, error = result.error.userMessage()) }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val result = repository.searchNotes(current.query.trim(), cursor)) {
                is VaultResult.Success -> _state.update {
                    it.copy(loading = false, results = it.results + result.value.items, nextCursor = result.value.nextCursor)
                }
                is VaultResult.Failure -> _state.update { it.copy(loading = false, error = result.error.userMessage()) }
            }
        }
    }
}
