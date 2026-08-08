package com.markrai.vaultist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.HeadingColorPalette
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThemePreferences {
    private val colorThemeKey = stringPreferencesKey("color_theme")
    private val appearanceKey = stringPreferencesKey("appearance")
    private val colorizedHeadingsKey = booleanPreferencesKey("colorized_headings")
    private val colorizeCheckboxStatusKey = booleanPreferencesKey("colorize_checkbox_status")
    private val headingColorPaletteKey = stringPreferencesKey("heading_color_palette")

    override val colorTheme: Flow<AppColorTheme> = context.vaultistDataStore.data.map { prefs ->
        AppColorTheme.fromId(prefs[colorThemeKey])
    }

    override suspend fun setColorTheme(theme: AppColorTheme) {
        context.vaultistDataStore.edit { it[colorThemeKey] = theme.id }
    }

    override val appearance: Flow<AppAppearance> = context.vaultistDataStore.data.map { prefs ->
        AppAppearance.fromId(prefs[appearanceKey])
    }

    override suspend fun setAppearance(appearance: AppAppearance) {
        context.vaultistDataStore.edit { it[appearanceKey] = appearance.id }
    }

    override val colorizedHeadings: Flow<Boolean> = context.vaultistDataStore.data.map { prefs ->
        prefs[colorizedHeadingsKey] ?: false
    }

    override suspend fun setColorizedHeadings(enabled: Boolean) {
        context.vaultistDataStore.edit { it[colorizedHeadingsKey] = enabled }
    }

    override val colorizeCheckboxStatus: Flow<Boolean> = context.vaultistDataStore.data.map { prefs ->
        prefs[colorizeCheckboxStatusKey] ?: false
    }

    override suspend fun setColorizeCheckboxStatus(enabled: Boolean) {
        context.vaultistDataStore.edit { it[colorizeCheckboxStatusKey] = enabled }
    }

    override val headingColorPalette: Flow<HeadingColorPalette> = context.vaultistDataStore.data.map { prefs ->
        HeadingColorPalette.fromId(prefs[headingColorPaletteKey])
    }

    override suspend fun setHeadingColorPalette(palette: HeadingColorPalette) {
        context.vaultistDataStore.edit { it[headingColorPaletteKey] = palette.id }
    }
}
