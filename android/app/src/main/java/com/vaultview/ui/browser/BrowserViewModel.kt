package com.vaultview.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultview.data.ask.AskOutcome
import com.vaultview.data.ask.AskStage
import com.vaultview.data.ask.VaultAskEngine
import com.vaultview.data.genai.LocalAiCapability
import com.vaultview.data.genai.PromptGenerationClient
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher

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
    val submittedQuestion: String? = null,
    val askAnswer: String? = null,
    val askSources: List<BrowseItem> = emptyList(),
    val askStage: AskStage? = null,
    val askCapability: LocalAiCapability = LocalAiCapability.Unchecked,
    val askSubmitting: Boolean = false,
    val askHadInvalidCitations: Boolean = false,
    val askMessage: String? = null,
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val askEngine: VaultAskEngine,
    private val promptClient: PromptGenerationClient,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state

    private var filesSearchJob: Job? = null
    private var askJob: Job? = null
    private var capabilityJob: Job? = null
    private var askRequestId = 0L

    init { loadBrowse("") }

    companion object {
        internal var askWorkDispatcher: CoroutineDispatcher = Dispatchers.Default
    }

    fun openFolder(folder: String) {
        invalidateAsk()
        clearSearch()
        loadBrowse(folder)
    }

    fun up() {
        val parent = _state.value.folder.substringBeforeLast('/', "")
        openFolder(parent)
    }

    fun retry() {
        when {
            _state.value.searchMode == SearchMode.Ask && needsCapabilityAction() ->
                recheckCapability()
            _state.value.searchMode == SearchMode.Ask && _state.value.submittedQuestion != null ->
                submitSearch()
            _state.value.isSearchResults -> runSearch(reset = true)
            else -> loadBrowse(_state.value.folder)
        }
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query, error = null) }
        when (_state.value.searchMode) {
            SearchMode.Files -> onFilesQueryChanged(query)
            SearchMode.Content -> Unit
            SearchMode.Ask -> {
                if (query.isBlank()) clearAskResults(keepQuery = true)
            }
        }
    }

    fun toggleSearchMode() {
        val nextMode = when (_state.value.searchMode) {
            SearchMode.Files -> SearchMode.Content
            SearchMode.Content -> SearchMode.Ask
            SearchMode.Ask -> SearchMode.Files
        }
        invalidateAsk()
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
                submittedQuestion = null,
                askAnswer = null,
                askSources = emptyList(),
                askStage = null,
                askSubmitting = false,
                askHadInvalidCitations = false,
                askMessage = null,
            )
        }
        when (nextMode) {
            SearchMode.Files -> onFilesQueryChanged(_state.value.query)
            SearchMode.Content -> loadBrowse(_state.value.folder, keepQuery = true)
            SearchMode.Ask -> recheckCapability()
        }
    }

    fun submitSearch() {
        when (_state.value.searchMode) {
            SearchMode.Content -> runSearch(reset = true)
            SearchMode.Ask -> submitAsk()
            SearchMode.Files -> Unit
        }
    }

    fun cancelAsk() {
        invalidateAsk()
        _state.update {
            it.copy(
                askSubmitting = false,
                askStage = null,
                searching = false,
            )
        }
    }

    fun recheckCapability() {
        if (capabilityJob?.isActive == true) return
        capabilityJob = viewModelScope.launch {
            _state.update { it.copy(askCapability = LocalAiCapability.Checking) }
            val capability = promptClient.checkCapability()
            if (_state.value.searchMode != SearchMode.Ask) return@launch
            when (capability) {
                LocalAiCapability.Downloadable -> {
                    // Skip the manual Download click: Ask mode implies consent to fetch the model.
                    _state.update { it.copy(askCapability = LocalAiCapability.Downloading()) }
                    val afterDownload = promptClient.downloadModel()
                    if (_state.value.searchMode != SearchMode.Ask) return@launch
                    _state.update { it.copy(askCapability = afterDownload) }
                }
                else -> _state.update { it.copy(askCapability = capability) }
            }
        }
    }

    fun downloadAskModel() {
        if (capabilityJob?.isActive == true &&
            _state.value.askCapability is LocalAiCapability.Downloading
        ) {
            return
        }
        capabilityJob?.cancel()
        capabilityJob = viewModelScope.launch {
            _state.update { it.copy(askCapability = LocalAiCapability.Downloading()) }
            val capability = promptClient.downloadModel()
            if (_state.value.searchMode != SearchMode.Ask) return@launch
            _state.update { it.copy(askCapability = capability) }
        }
    }

    fun onAskResumed() {
        if (_state.value.searchMode != SearchMode.Ask) return
        when (_state.value.askCapability) {
            is LocalAiCapability.Ready,
            LocalAiCapability.Checking,
            is LocalAiCapability.Downloading,
            -> Unit
            else -> recheckCapability()
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

    private fun submitAsk() {
        val question = _state.value.query.trim()
        if (question.isEmpty() || _state.value.askSubmitting) return

        val requestId = ++askRequestId
        askJob?.cancel()
        askJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    askSubmitting = true,
                    searching = true,
                    submittedQuestion = question,
                    askAnswer = null,
                    askSources = emptyList(),
                    askStage = AskStage.CheckingOnDeviceAi,
                    askHadInvalidCitations = false,
                    askMessage = null,
                    error = null,
                    isSearchResults = true,
                    searched = true,
                    items = emptyList(),
                )
            }

            val outcome = withContext(askWorkDispatcher) {
                val capability = ensureCapabilityReady()
                if (requestId != askRequestId) return@withContext AskOutcome.Cancelled
                if (capability !is LocalAiCapability.Ready) {
                    return@withContext AskOutcome.Failure(capabilityMessage(capability), retryable = true)
                }

                askEngine.ask(
                    question = question,
                    requestId = requestId,
                    isActive = { requestId == askRequestId },
                    onStage = { stage ->
                        if (requestId == askRequestId) {
                            _state.update {
                                it.copy(
                                    askStage = stage,
                                    searching = stage == AskStage.SearchingHost || stage == AskStage.LoadingNotes,
                                )
                            }
                        }
                    },
                )
            }

            if (requestId != askRequestId) return@launch
            publishAskOutcome(outcome)
        }
    }

    private suspend fun ensureCapabilityReady(): LocalAiCapability {
        val current = _state.value.askCapability
        if (current is LocalAiCapability.Ready) return current
        val checked = promptClient.checkCapability()
        if (_state.value.searchMode == SearchMode.Ask) {
            _state.update { it.copy(askCapability = checked) }
        }
        return checked
    }

    private fun publishAskOutcome(outcome: AskOutcome) {
        when (outcome) {
            is AskOutcome.Success -> _state.update {
                it.copy(
                    askSubmitting = false,
                    searching = false,
                    askStage = null,
                    askAnswer = outcome.answer,
                    askSources = outcome.sources,
                    askHadInvalidCitations = outcome.hadInvalidCitations,
                    askMessage = null,
                    error = null,
                )
            }
            is AskOutcome.NoMatches -> _state.update {
                it.copy(
                    askSubmitting = false,
                    searching = false,
                    askStage = null,
                    askAnswer = null,
                    askSources = emptyList(),
                    askMessage = outcome.message,
                    error = null,
                )
            }
            is AskOutcome.Partial -> _state.update {
                it.copy(
                    askSubmitting = false,
                    searching = false,
                    askStage = null,
                    askAnswer = null,
                    askSources = outcome.sources,
                    askMessage = outcome.message,
                    error = null,
                )
            }
            is AskOutcome.Failure -> _state.update {
                it.copy(
                    askSubmitting = false,
                    searching = false,
                    askStage = null,
                    askAnswer = null,
                    askSources = emptyList(),
                    askMessage = null,
                    error = outcome.message,
                )
            }
            AskOutcome.Cancelled -> _state.update {
                it.copy(
                    askSubmitting = false,
                    searching = false,
                    askStage = null,
                )
            }
        }
    }

    private fun needsCapabilityAction(): Boolean {
        return when (_state.value.askCapability) {
            is LocalAiCapability.Downloadable,
            is LocalAiCapability.Failed,
            LocalAiCapability.Unavailable,
            LocalAiCapability.Unchecked,
            -> true
            else -> false
        }
    }

    private fun capabilityMessage(capability: LocalAiCapability): String = when (capability) {
        LocalAiCapability.Unavailable,
        LocalAiCapability.Unchecked,
        is LocalAiCapability.Failed,
        -> "On-device AI is currently unavailable."
        LocalAiCapability.Downloadable -> "On-device AI needs a one-time model download."
        is LocalAiCapability.Downloading -> "Downloading on-device AI model…"
        LocalAiCapability.Checking -> "Checking on-device AI…"
        is LocalAiCapability.Ready -> ""
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

    private fun clearAskResults(keepQuery: Boolean = false) {
        invalidateAsk()
        _state.update {
            it.copy(
                query = if (keepQuery) it.query else "",
                submittedQuestion = null,
                askAnswer = null,
                askSources = emptyList(),
                askStage = null,
                askSubmitting = false,
                askHadInvalidCitations = false,
                askMessage = null,
                searching = false,
                isSearchResults = false,
                searched = false,
            )
        }
    }

    private fun invalidateAsk() {
        askRequestId += 1
        askJob?.cancel()
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
