package com.markrai.vaultist.di

import com.markrai.vaultist.di.config.AskRuntimeConfig
import com.markrai.vaultist.di.config.BrowseUiConfig
import com.markrai.vaultist.di.config.NetworkConfig
import com.markrai.vaultist.di.config.NoteWidgetConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConfigModule {
    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig = NetworkConfig()

    @Provides
    @Singleton
    fun provideAskRuntimeConfig(): AskRuntimeConfig = AskRuntimeConfig()

    @Provides
    @Singleton
    fun provideBrowseUiConfig(): BrowseUiConfig = BrowseUiConfig()

    @Provides
    @Singleton
    fun provideNoteWidgetConfig(): NoteWidgetConfig = NoteWidgetConfig()
}
