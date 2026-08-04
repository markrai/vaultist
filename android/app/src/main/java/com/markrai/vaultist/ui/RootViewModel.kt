package com.markrai.vaultist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ConfigurationState {
    data object Loading : ConfigurationState
    data object Missing : ConfigurationState
    data object Configured : ConfigurationState
}

@HiltViewModel
class RootViewModel @Inject constructor(repository: VaultRepository) : ViewModel() {
    val configuration: StateFlow<ConfigurationState> = repository.serverUrl
        .map { if (it.isNullOrBlank()) ConfigurationState.Missing else ConfigurationState.Configured }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfigurationState.Loading)
}
