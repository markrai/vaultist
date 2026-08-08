package com.markrai.vaultist.ui.widget

import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.data.settings.ThemePreferences
import com.markrai.vaultist.data.widget.NoteWidgetLoader
import com.markrai.vaultist.data.widget.NoteWidgetRefresher
import com.markrai.vaultist.data.widget.NoteWidgetStore
import com.markrai.vaultist.di.config.NoteWidgetConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun noteWidgetLoader(): NoteWidgetLoader
    fun noteWidgetStore(): NoteWidgetStore
    fun noteWidgetConfig(): NoteWidgetConfig
    fun themePreferences(): ThemePreferences
    fun vaultRepository(): VaultRepository
    fun noteWidgetRefresher(): NoteWidgetRefresher
}
