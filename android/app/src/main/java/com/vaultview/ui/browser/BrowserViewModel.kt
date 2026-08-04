package com.vaultview.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultview.data.repository.VaultRepository
import com.vaultview.domain.BrowseItem
import com.vaultview.domain.SearchMode
import com.vaultview.domain.VaultMetadata
import com.vaultview.domain.VaultResult
import com.vaultview.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val folder: String = "",
    val vault: VaultMetadata? = null,
    val items: List<BrowseItem> = emptyList(),
    val nextCursor: String? = null,
    val query: String = "",
    val searchMode: SearchMode = SearchMode.Files,
    val searching: Boolean = false,
    val isSearchResults: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BrowserViewModel @Inject constructor(private val repository: VaultRepository) : ViewModel() {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state

    private var filesSearchJob: Job? = null

    init { loadBrowse("") }

    fun openFolder(folder: String) {
        clearSearch()
        loadBrowse(folder)
    }

    fun up() {
        val parent = _state.value.folder.substringBeforeLast('/', "")
        openFolder(parent)
    }

    fun retry() {
        if (_state.value.isSearchResults) {
            runSearch(reset = true)
        } else {
            loadBrowse(_state.value.folder)
        }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query, error = null) }
        when (_state.value.searchMode) {
            SearchMode.Files -> onFilesQueryChanged(query)
            SearchMode.Content -> Unit
        }
    }

    fun toggleSearchMode() {
        val nextMode = when (_state.value.searchMode) {
            SearchMode.Files -> SearchMode.Content
            SearchMode.Content -> SearchMode.Files
        }
        filesSearchJob?.cancel()
        _state.update {
            it.copy(
                searchMode = nextMode,
                searching = false,
                searched = false,
                isSearchResults = false,
                items = emptyList(),
                nextCursor = null,
                error = null,
            )
        }
        when (nextMode) {
            SearchMode.Files -> onFilesQueryChanged(_state.value.query)
            SearchMode.Content -> loadBrowse(_state.value.folder, keepQuery = true)
        }
    }

    fun submitSearch() {
        if (_state.value.searchMode != SearchMode.Content) return
        runSearch(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            if (current.isSearchResults) {
                when (val result = repository.searchNotes(current.query.trim(), current.searchMode, cursor)) {
                    is VaultResult.Success -> _state.update {
                        it.copy(loadingMore = false, items = it.items + result.value.items, nextCursor = result.value.nextCursor)
                    }
                    is VaultResult.Failure -> _state.update { it.copy(loadingMore = false, error = result.error.userMessage()) }
                }
            } else {
                when (val result = repository.listNotes(current.folder, cursor)) {
                    is VaultResult.Success -> _state.update {
                        it.copy(loadingMore = false, items = it.items + result.value.items, nextCursor = result.value.nextCursor)
                    }
                    is VaultResult.Failure -> _state.update { it.copy(loadingMore = false, error = result.error.userMessage()) }
                }
            }
        }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            when (val result = repository.refreshIndex()) {
                is VaultResult.Failure -> _state.update { it.copy(refreshing = false, error = result.error.userMessage()) }
                is VaultResult.Success -> {
                    repeat(30) {
                        delay(500)
                        val status = repository.getIndexStatus()
                        if (status is VaultResult.Success && status.value.state != "indexing") {
                            if (_state.value.isSearchResults) {
                                runSearch(reset = true, refreshing = true)
                            } else {
                                loadBrowse(_state.value.folder, refreshing = true)
                            }
                            return@launch
                        }
                    }
                    _state.update { it.copy(refreshing = false, error = "Refresh is still running. You can continue browsing.") }
                }
            }
        }
    }

    private fun onFilesQueryChanged(query: String) {
        filesSearchJob?.cancel()
        if (query.isBlank()) {
            loadBrowse(_state.value.folder, keepQuery = true)
            return
        }
        filesSearchJob = viewModelScope.launch {
            delay(300)
            runSearch(reset = true)
        }
    }

    private fun clearSearch() {
        filesSearchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                searching = false,
                searched = false,
                isSearchResults = false,
            )
        }
    }

    private fun runSearch(reset: Boolean = false, refreshing: Boolean = false) {
        val current = _state.value
        val query = current.query.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    searching = !refreshing,
                    loading = reset && !refreshing,
                    refreshing = refreshing,
                    error = null,
                    searched = true,
                    isSearchResults = true,
                    items = if (reset) emptyList() else it.items,
                    nextCursor = if (reset) null else it.nextCursor,
                )
            }
            when (val result = repository.searchNotes(query, current.searchMode)) {
                is VaultResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        searching = false,
                        refreshing = false,
                        items = result.value.items,
                        nextCursor = result.value.nextCursor,
                    )
                }
                is VaultResult.Failure -> _state.update {
                    it.copy(loading = false, searching = false, refreshing = false, error = result.error.userMessage())
                }
            }
        }
    }

    private fun loadBrowse(folder: String, refreshing: Boolean = false, keepQuery: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    refreshing = refreshing,
                    folder = folder,
                    error = null,
                    items = emptyList(),
                    nextCursor = null,
                    isSearchResults = false,
                    searched = false,
                    searching = false,
                    query = if (keepQuery) it.query else "",
                )
            }
            val vault = repository.getVault()
            when (val page = repository.listNotes(folder)) {
                is VaultResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        vault = (vault as? VaultResult.Success)?.value ?: it.vault,
                        items = page.value.items,
                        nextCursor = page.value.nextCursor,
                    )
                }
                is VaultResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = page.error.userMessage())
                }
            }
        }
    }
}
