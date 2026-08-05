package com.markrai.vaultist.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.data.settings.BrowseSortPreferences
import com.markrai.vaultist.di.config.BrowseUiConfig
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.BrowsePage
import com.markrai.vaultist.domain.BrowseSortMode
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.SearchPage
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
import kotlinx.coroutines.flow.first
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
    val sortMode: BrowseSortMode = BrowseSortMode.Alphabetical,
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
    private val browseSortPreferences: BrowseSortPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state

    private var filesSearchJob: Job? = null
    private val pendingDeletedNoteIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val mode = browseSortPreferences.sortMode.first()
            _state.update { current ->
                current.copy(
                    sortMode = mode,
                    items = sortBrowseItems(current.items, mode),
                )
            }
        }
        loadBrowse("")
    }

    fun toggleSortMode() {
        viewModelScope.launch {
            val next = when (_state.value.sortMode) {
                BrowseSortMode.Alphabetical -> BrowseSortMode.ModifiedDesc
                BrowseSortMode.ModifiedDesc -> BrowseSortMode.Alphabetical
            }
            browseSortPreferences.setSortMode(next)
            _state.update {
                it.copy(
                    sortMode = next,
                    items = sortBrowseItems(it.items, next),
                )
            }
        }
    }

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
                    is VaultResult.Success -> {
                        val merged = sortBrowseItems(
                            current.items + result.value.items,
                            current.sortMode,
                        )
                        _state.update {
                            it.copy(
                                loadingMore = false,
                                items = merged,
                                nextCursor = result.value.nextCursor,
                            )
                        }
                    }
                    is VaultResult.Failure -> _state.update { it.copy(loadingMore = false, error = result.error.userMessage()) }
                }
            } else {
                when (val result = repository.listNotes(current.folder, cursor)) {
                    is VaultResult.Success -> {
                        val merged = sortBrowseItems(
                            current.items + result.value.items,
                            current.sortMode,
                        )
                        _state.update {
                            it.copy(
                                loadingMore = false,
                                items = merged,
                                nextCursor = result.value.nextCursor,
                            )
                        }
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
                                loadBrowse(_state.value.folder, refreshing = true, clearPendingDeletes = true)
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
            modifiedAt = note.modifiedAt.takeIf { it.isNotBlank() },
        )
        _state.update { state ->
            state.copy(items = sortBrowseItems(state.items + item, state.sortMode))
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
        pendingDeletedNoteIds.add(noteId)
        _state.update {
            it.copy(
                query = "",
                searching = false,
                searched = false,
                isSearchResults = false,
                error = null,
                items = it.items.filter { item -> item.id !in pendingDeletedNoteIds },
            )
        }
        loadBrowse(_state.value.folder)
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
            when (val firstPage = repository.searchNotes(query, current.searchMode)) {
                is VaultResult.Success -> {
                    when (val allItems = fetchAllSearchItems(query, current.searchMode, firstPage.value)) {
                        is VaultResult.Success -> _state.update {
                            it.copy(
                                searching = false,
                                refreshing = false,
                                items = sortBrowseItems(allItems.value, it.sortMode),
                                nextCursor = null,
                            )
                        }
                        is VaultResult.Failure -> _state.update {
                            it.copy(searching = false, refreshing = false, error = allItems.error.userMessage())
                        }
                    }
                }
                is VaultResult.Failure -> _state.update {
                    it.copy(searching = false, refreshing = false, error = firstPage.error.userMessage())
                }
            }
        }
    }

    private fun loadBrowse(
        folder: String,
        refreshing: Boolean = false,
        keepQuery: Boolean = false,
        clearPendingDeletes: Boolean = false,
    ) {
        viewModelScope.launch {
            if (clearPendingDeletes) {
                pendingDeletedNoteIds.clear()
            }
            val sortMode = _state.value.sortMode
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
            when (val firstPage = repository.listNotes(folder)) {
                is VaultResult.Success -> {
                    when (val allItems = fetchAllBrowseItems(folder, firstPage.value)) {
                        is VaultResult.Success -> {
                            val items = allItems.value.filter { item ->
                                item.id !in pendingDeletedNoteIds
                            }
                            _state.update {
                                it.copy(
                                    loading = false,
                                    refreshing = false,
                                    vault = (vault as? VaultResult.Success)?.value ?: it.vault,
                                    items = sortBrowseItems(items, sortMode),
                                    nextCursor = null,
                                )
                            }
                        }
                        is VaultResult.Failure -> _state.update {
                            it.copy(loading = false, refreshing = false, error = allItems.error.userMessage())
                        }
                    }
                }
                is VaultResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = firstPage.error.userMessage())
                }
            }
        }
    }

    private suspend fun fetchAllBrowseItems(
        folder: String,
        firstPage: BrowsePage,
    ): VaultResult<List<BrowseItem>> = fetchAllPages(
        initialItems = firstPage.items,
        initialCursor = firstPage.nextCursor,
    ) { cursor ->
        when (val page = repository.listNotes(folder, cursor)) {
            is VaultResult.Success -> VaultResult.Success(PagedItems(page.value.items, page.value.nextCursor))
            is VaultResult.Failure -> page
        }
    }

    private suspend fun fetchAllSearchItems(
        query: String,
        searchMode: SearchMode,
        firstPage: SearchPage,
    ): VaultResult<List<BrowseItem>> = fetchAllPages(
        initialItems = firstPage.items,
        initialCursor = firstPage.nextCursor,
    ) { cursor ->
        when (val page = repository.searchNotes(query, searchMode, cursor)) {
            is VaultResult.Success -> VaultResult.Success(PagedItems(page.value.items, page.value.nextCursor))
            is VaultResult.Failure -> page
        }
    }

    private suspend fun fetchAllPages(
        initialItems: List<BrowseItem>,
        initialCursor: String?,
        fetchPage: suspend (String) -> VaultResult<PagedItems>,
    ): VaultResult<List<BrowseItem>> {
        var items = initialItems
        var cursor = initialCursor
        var previousCursor: String? = null
        while (cursor != null) {
            if (cursor == previousCursor) break
            previousCursor = cursor
            when (val page = fetchPage(cursor)) {
                is VaultResult.Success -> {
                    items = items + page.value.items
                    cursor = page.value.nextCursor
                }
                is VaultResult.Failure -> return VaultResult.Failure(page.error)
            }
        }
        return VaultResult.Success(items)
    }

    private data class PagedItems(
        val items: List<BrowseItem>,
        val nextCursor: String?,
    )
}
