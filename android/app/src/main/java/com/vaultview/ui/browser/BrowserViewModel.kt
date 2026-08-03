package com.vaultview.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultview.data.repository.VaultRepository
import com.vaultview.domain.BrowseItem
import com.vaultview.domain.VaultMetadata
import com.vaultview.domain.VaultResult
import com.vaultview.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val error: String? = null,
)

@HiltViewModel
class BrowserViewModel @Inject constructor(private val repository: VaultRepository) : ViewModel() {
    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state

    init { load("") }

    fun openFolder(folder: String) = load(folder)

    fun up() {
        val parent = _state.value.folder.substringBeforeLast('/', "")
        load(parent)
    }

    fun retry() = load(_state.value.folder)

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            when (val result = repository.listNotes(current.folder, cursor)) {
                is VaultResult.Success -> _state.update {
                    it.copy(loadingMore = false, items = it.items + result.value.items, nextCursor = result.value.nextCursor)
                }
                is VaultResult.Failure -> _state.update { it.copy(loadingMore = false, error = result.error.userMessage()) }
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
                            load(_state.value.folder, refreshing = true)
                            return@launch
                        }
                    }
                    _state.update { it.copy(refreshing = false, error = "Refresh is still running. You can continue browsing.") }
                }
            }
        }
    }

    private fun load(folder: String, refreshing: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, refreshing = refreshing, folder = folder, error = null, items = emptyList()) }
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
