package com.markrai.vaultist.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.di.config.BrowseUiConfig
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.VaultMetadata
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.ui.userMessage
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
class BrowserViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val browseUiConfig: BrowseUiConfig,
    private val pendingBrowseSync: PendingBrowseSync,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state

    private var filesSearchJob: Job? = null

    init { loadBrowse("") }

    fun onReturnedToBrowse() {
        pendingBrowseSync.consumeAfterDelete()?.let { afterNoteDeleted(it) }
    }

    fun openFolder(folder: String) {
        clearSearch()
        loadBrowse(folder)
    }

    fun up() {
        val parent = _state.value.folder.substringBeforeLast('/', "")
        openFolder(parent)
    }

    fun retry() {
        when {
            _state.value.isSearchResults -> runSearch(reset = true)
            else -> loadBrowse(_state.value.folder)
        }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query, error = null) }
        when (_state.value.searchMode) {
            SearchMode.Files -> onFilesQueryChanged(query)
            SearchMode.Content, SearchMode.Ask -> Unit
        }
    }

    fun toggleSearchMode() {
        val nextMode = when (_state.value.searchMode) {
            SearchMode.Files -> SearchMode.Content
            SearchMode.Content -> SearchMode.Ask
            SearchMode.Ask -> SearchMode.Files
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
            SearchMode.Ask -> Unit
        }
    }

    fun submitSearch() {
        when (_state.value.searchMode) {
            SearchMode.Content -> runSearch(reset = true)
            SearchMode.Files, SearchMode.Ask -> Unit
        }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loadingMore || current.searchMode == SearchMode.Ask) return
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
        if (_state.value.refreshing || _state.value.searchMode == SearchMode.Ask) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            when (val result = repository.refreshIndex()) {
                is VaultResult.Failure -> _state.update { it.copy(refreshing = false, error = result.error.userMessage()) }
                is VaultResult.Success -> {
                    repeat(browseUiConfig.indexPollAttempts) {
                        delay(browseUiConfig.indexPollDelayMs)
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

    /**
     * Shows a just-created note in the current folder before the index catches up.
     * Browse-only; create orchestration stays in [com.markrai.vaultist.ui.create.CreateNoteViewModel].
     */
    fun includeCreatedNote(note: Note) {
        val current = _state.value
        if (current.isSearchResults || current.searchMode == SearchMode.Ask) return
        val parent = note.id.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent != current.folder) return
        if (current.items.any { it.id == note.id }) return
        val item = BrowseItem(
            kind = BrowseKind.Note,
            id = note.id,
            name = note.filename,
            title = note.title,
            path = note.path,
            error = note.error?.takeIf { it.isNotBlank() },
        )
        _state.update { state ->
            val folders = state.items.filter { it.kind == BrowseKind.Folder }
            val notes = (state.items.filter { it.kind == BrowseKind.Note } + item)
                .sortedBy { it.name.lowercase() }
            state.copy(items = folders + notes)
        }
    }

    /** Wait for a write-triggered reindex, then reload browse from the server. */
    fun reconcileAfterMutation() {
        if (_state.value.searchMode == SearchMode.Ask) return
        viewModelScope.launch {
            repeat(browseUiConfig.indexPollAttempts) {
                delay(browseUiConfig.indexPollDelayMs)
                val status = repository.getIndexStatus()
                if (status is VaultResult.Success && status.value.state != "indexing") {
                    if (!_state.value.isSearchResults) {
                        loadBrowse(_state.value.folder, keepQuery = true)
                    }
                    return@launch
                }
            }
        }
    }

    /**
     * After a note is deleted: leave search, drop the note from the current list,
     * then reload the folder once the delete-triggered reindex finishes.
     */
    fun afterNoteDeleted(noteId: String) {
        if (_state.value.searchMode == SearchMode.Ask) return
        filesSearchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                searching = false,
                searched = false,
                isSearchResults = false,
                error = null,
            )
        }
        loadBrowse(_state.value.folder, excludeNoteId = noteId)
        reconcileAfterMutation()
    }

    private fun onFilesQueryChanged(query: String) {
        filesSearchJob?.cancel()
        if (query.isBlank()) {
            loadBrowse(_state.value.folder, keepQuery = true)
            return
        }
        filesSearchJob = viewModelScope.launch {
            delay(browseUiConfig.debounceMs)
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
                    searching = true,
                    refreshing = refreshing,
                    error = null,
                    searched = true,
                    isSearchResults = true,
                    nextCursor = if (reset) null else it.nextCursor,
                )
            }
            when (val result = repository.searchNotes(query, current.searchMode)) {
                is VaultResult.Success -> _state.update {
                    it.copy(
                        searching = false,
                        refreshing = false,
                        items = result.value.items,
                        nextCursor = result.value.nextCursor,
                    )
                }
                is VaultResult.Failure -> _state.update {
                    it.copy(searching = false, refreshing = false, error = result.error.userMessage())
                }
            }
        }
    }

    private fun loadBrowse(
        folder: String,
        refreshing: Boolean = false,
        keepQuery: Boolean = false,
        excludeNoteId: String? = null,
    ) {
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
                    val items = if (excludeNoteId == null) {
                        page.value.items
                    } else {
                        page.value.items.filter { item -> item.id != excludeNoteId }
                    }
                    it.copy(
                        loading = false,
                        refreshing = false,
                        vault = (vault as? VaultResult.Success)?.value ?: it.vault,
                        items = items,
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
