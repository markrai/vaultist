package com.markrai.vaultist.data.settings

import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.HeadingColorPalette
import kotlinx.coroutines.flow.Flow

interface ThemePreferences {
    val colorTheme: Flow<AppColorTheme>
    suspend fun setColorTheme(theme: AppColorTheme)

    val appearance: Flow<AppAppearance>
    suspend fun setAppearance(appearance: AppAppearance)

    val colorizedHeadings: Flow<Boolean>
    suspend fun setColorizedHeadings(enabled: Boolean)

    val headingColorPalette: Flow<HeadingColorPalette>
    suspend fun setHeadingColorPalette(palette: HeadingColorPalette)
}
