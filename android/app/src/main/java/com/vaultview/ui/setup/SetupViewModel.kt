package com.vaultview.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultview.BuildConfig
import com.vaultview.data.api.normalizeServerUrl
import com.vaultview.data.repository.VaultRepository
import com.vaultview.domain.VaultResult
import com.vaultview.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val url: String = BuildConfig.DEVELOPMENT_SERVER_URL,
    val testing: Boolean = false,
    val testedUrl: String? = null,
    val message: String? = null,
    val valid: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(private val repository: VaultRepository) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state

    init {
        viewModelScope.launch {
            repository.serverUrl.first()?.let { configured -> _state.update { it.copy(url = configured) } }
        }
    }

    fun updateUrl(value: String) {
        _state.update { it.copy(url = value, valid = it.testedUrl == value && it.valid, message = null, saved = false) }
    }

    fun testConnection() {
        if (_state.value.testing) return
        val draft = _state.value.url
        runCatching { normalizeServerUrl(draft) }.onFailure {
            _state.update { it.copy(valid = false, message = it.message ?: "Enter a valid URL") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true, valid = false, message = null) }
            when (val result = repository.testServer(draft)) {
                is VaultResult.Success -> _state.update {
                    it.copy(testing = false, testedUrl = draft, valid = true, message = "Connected. Index is ${result.value.state}.")
                }
                is VaultResult.Failure -> _state.update {
                    it.copy(testing = false, testedUrl = draft, valid = false, message = result.error.userMessage())
                }
            }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.valid || current.testedUrl != current.url) return
        viewModelScope.launch {
            repository.saveServer(current.url)
            _state.update { it.copy(saved = true) }
        }
    }
}
