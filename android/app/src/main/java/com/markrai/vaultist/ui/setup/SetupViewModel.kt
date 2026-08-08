package com.markrai.vaultist.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.BuildConfig
import com.markrai.vaultist.data.api.normalizeServerUrl
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.data.settings.AskPreferences
import com.markrai.vaultist.data.widget.NoteWidgetRefresher
import com.markrai.vaultist.data.settings.DateTimeInsertPreferences
import com.markrai.vaultist.data.settings.ModifiedDatePreferences
import com.markrai.vaultist.data.settings.ThemePreferences
import com.markrai.vaultist.domain.DateTimeInsertFormat
import com.markrai.vaultist.domain.ModifiedDateStyle
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.HeadingColorPalette
import com.markrai.vaultist.ui.userMessage
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
    val enableAskThinking: Boolean = false,
    val colorTheme: AppColorTheme = AppColorTheme.Ruby,
    val appearance: AppAppearance = AppAppearance.Light,
    val colorizedHeadings: Boolean = false,
    val colorizeCheckboxStatus: Boolean = false,
    val headingColorPalette: HeadingColorPalette = HeadingColorPalette.Classic,
    val relativeModifiedDates: Boolean = false,
    val dateTimeInsertFormat: DateTimeInsertFormat = DateTimeInsertFormat.IsoDateTime,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val askPreferences: AskPreferences,
    private val themePreferences: ThemePreferences,
    private val modifiedDatePreferences: ModifiedDatePreferences,
    private val dateTimeInsertPreferences: DateTimeInsertPreferences,
    private val noteWidgetRefresh: NoteWidgetRefresher,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state

    init {
        viewModelScope.launch {
            repository.serverUrl.first()?.let { configured -> _state.update { it.copy(url = configured) } }
        }
        viewModelScope.launch {
            askPreferences.enableAskThinking.collect { enabled ->
                _state.update { it.copy(enableAskThinking = enabled) }
            }
        }
        viewModelScope.launch {
            themePreferences.colorTheme.collect { theme ->
                _state.update { it.copy(colorTheme = theme) }
            }
        }
        viewModelScope.launch {
            themePreferences.appearance.collect { appearance ->
                _state.update { it.copy(appearance = appearance) }
            }
        }
        viewModelScope.launch {
            themePreferences.colorizedHeadings.collect { enabled ->
                _state.update { it.copy(colorizedHeadings = enabled) }
            }
        }
        viewModelScope.launch {
            themePreferences.colorizeCheckboxStatus.collect { enabled ->
                _state.update { it.copy(colorizeCheckboxStatus = enabled) }
            }
        }
        viewModelScope.launch {
            themePreferences.headingColorPalette.collect { palette ->
                _state.update { it.copy(headingColorPalette = palette) }
            }
        }
        viewModelScope.launch {
            modifiedDatePreferences.style.collect { style ->
                _state.update { it.copy(relativeModifiedDates = style == ModifiedDateStyle.Relative) }
            }
        }
        viewModelScope.launch {
            dateTimeInsertPreferences.format.collect { format ->
                _state.update { it.copy(dateTimeInsertFormat = format) }
            }
        }
    }

    fun updateUrl(value: String) {
        _state.update { it.copy(url = value, valid = it.testedUrl == value && it.valid, message = null, saved = false) }
    }

    fun setEnableAskThinking(enabled: Boolean) {
        viewModelScope.launch {
            askPreferences.setEnableAskThinking(enabled)
        }
    }

    fun setColorTheme(theme: AppColorTheme) {
        viewModelScope.launch {
            themePreferences.setColorTheme(theme)
        }
    }

    fun setAppearance(appearance: AppAppearance) {
        viewModelScope.launch {
            themePreferences.setAppearance(appearance)
        }
    }

    fun setColorizedHeadings(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setColorizedHeadings(enabled)
            noteWidgetRefresh.refreshAll()
        }
    }

    fun setColorizeCheckboxStatus(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setColorizeCheckboxStatus(enabled)
            noteWidgetRefresh.refreshAll()
        }
    }

    fun setHeadingColorPalette(palette: HeadingColorPalette) {
        viewModelScope.launch {
            themePreferences.setHeadingColorPalette(palette)
            noteWidgetRefresh.refreshAll()
        }
    }

    fun setRelativeModifiedDates(enabled: Boolean) {
        viewModelScope.launch {
            modifiedDatePreferences.setStyle(
                if (enabled) ModifiedDateStyle.Relative else ModifiedDateStyle.Absolute,
            )
        }
    }

    fun setDateTimeInsertFormat(format: DateTimeInsertFormat) {
        viewModelScope.launch {
            dateTimeInsertPreferences.setFormat(format)
        }
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
            noteWidgetRefresh.clearAllAndRefresh()
            _state.update { it.copy(saved = true) }
        }
    }
}
