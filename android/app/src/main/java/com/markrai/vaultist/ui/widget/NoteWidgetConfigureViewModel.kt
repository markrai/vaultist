package com.markrai.vaultist.ui.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.data.widget.NoteWidgetStore
import com.markrai.vaultist.di.config.BrowseUiConfig
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.VaultResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteWidgetConfigureUiState(
    val appWidgetId: Int? = null,
    val serverConfigured: Boolean = false,
    val query: String = "",
    val items: List<BrowseItem> = emptyList(),
    val selectedNoteId: String? = null,
    val loading: Boolean = true,
    val searching: Boolean = false,
    val error: String? = null,
    val binding: Boolean = false,
)

@HiltViewModel
class NoteWidgetConfigureViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val preferences: NoteWidgetStore,
    private val browseUiConfig: BrowseUiConfig,
) : ViewModel() {
    private val _state = MutableStateFlow(NoteWidgetConfigureUiState())
    val state: StateFlow<NoteWidgetConfigureUiState> = _state
    private var searchJob: Job? = null

    fun initialize(appWidgetId: Int) {
        if (_state.value.appWidgetId != null) return
        _state.update { it.copy(appWidgetId = appWidgetId) }
        viewModelScope.launch {
            val configured = !repository.serverUrl.first().isNullOrBlank()
            _state.update { it.copy(serverConfigured = configured, loading = configured) }
            if (configured) {
                loadRootNotes()
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun updateQuery(value: String) {
        _state.update { it.copy(query = value, error = null) }
        searchJob?.cancel()
        if (value.isBlank()) {
            loadRootNotes()
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            delay(browseUiConfig.debounceMs)
            when (val result = repository.searchNotes(value.trim(), SearchMode.Files)) {
                is VaultResult.Success -> {
                    val notes = result.value.items.filter { it.kind == BrowseKind.Note && !it.id.isNullOrBlank() }
                    _state.update { it.copy(items = notes, searching = false, loading = false, error = null) }
                }
                is VaultResult.Failure -> {
                    _state.update {
                        it.copy(
                            items = emptyList(),
                            searching = false,
                            loading = false,
                            error = "Could not search notes.",
                        )
                    }
                }
            }
        }
    }

    fun selectNote(noteId: String) {
        _state.update { it.copy(selectedNoteId = noteId) }
    }

    suspend fun confirmBinding(): String? {
        val widgetId = _state.value.appWidgetId ?: return null
        val noteId = _state.value.selectedNoteId ?: return null
        if (_state.value.binding) return null
        _state.update { it.copy(binding = true, error = null) }
        return try {
            preferences.setBinding(widgetId, noteId)
            noteId
        } catch (_: Exception) {
            _state.update { it.copy(binding = false, error = "Could not save widget settings.") }
            null
        }
    }

    private fun loadRootNotes() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, searching = false, error = null) }
            when (val result = repository.listNotes(folder = "")) {
                is VaultResult.Success -> {
                    val notes = result.value.items.filter { it.kind == BrowseKind.Note && !it.id.isNullOrBlank() }
                    _state.update { it.copy(items = notes, loading = false) }
                }
                is VaultResult.Failure -> {
                    _state.update {
                        it.copy(
                            items = emptyList(),
                            loading = false,
                            error = "Could not load notes.",
                        )
                    }
                }
            }
        }
    }
}
