package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.settings.ThemePreferences
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.HeadingColorPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeThemePreferences(
    initialColorTheme: AppColorTheme = AppColorTheme.Ruby,
    initialAppearance: AppAppearance = AppAppearance.Light,
    initialHeadingColorPalette: HeadingColorPalette = HeadingColorPalette.Classic,
) : ThemePreferences {
    private val _colorTheme = MutableStateFlow(initialColorTheme)
    override val colorTheme: Flow<AppColorTheme> = _colorTheme.asStateFlow()

    private val _appearance = MutableStateFlow(initialAppearance)
    override val appearance: Flow<AppAppearance> = _appearance.asStateFlow()

    private val _colorizedHeadings = MutableStateFlow(false)
    override val colorizedHeadings: Flow<Boolean> = _colorizedHeadings.asStateFlow()

    private val _colorizeCheckboxStatus = MutableStateFlow(false)
    override val colorizeCheckboxStatus: Flow<Boolean> = _colorizeCheckboxStatus.asStateFlow()

    private val _headingColorPalette = MutableStateFlow(initialHeadingColorPalette)
    override val headingColorPalette: Flow<HeadingColorPalette> = _headingColorPalette.asStateFlow()

    override suspend fun setColorTheme(theme: AppColorTheme) {
        _colorTheme.value = theme
    }

    override suspend fun setAppearance(appearance: AppAppearance) {
        _appearance.value = appearance
    }

    override suspend fun setColorizedHeadings(enabled: Boolean) {
        _colorizedHeadings.value = enabled
    }

    override suspend fun setColorizeCheckboxStatus(enabled: Boolean) {
        _colorizeCheckboxStatus.value = enabled
    }

    override suspend fun setHeadingColorPalette(palette: HeadingColorPalette) {
        _headingColorPalette.value = palette
    }
}
