package com.markrai.vaultist.di

import com.markrai.vaultist.data.repository.DefaultVaultRepository
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.data.settings.AskPreferences
import com.markrai.vaultist.data.settings.DataStoreAskPreferences
import com.markrai.vaultist.data.settings.BrowseSortPreferences
import com.markrai.vaultist.data.settings.BrowseViewPreferences
import com.markrai.vaultist.data.settings.DataStoreBrowseSortPreferences
import com.markrai.vaultist.data.settings.DataStoreBrowseViewPreferences
import com.markrai.vaultist.data.settings.DataStoreModifiedDatePreferences
import com.markrai.vaultist.data.settings.DataStoreThemePreferences
import com.markrai.vaultist.data.settings.DateTimeInsertFormatter
import com.markrai.vaultist.data.settings.DefaultDateTimeInsertFormatter
import com.markrai.vaultist.data.settings.ModifiedDatePreferences
import com.markrai.vaultist.data.settings.ServerSettings
import com.markrai.vaultist.data.settings.ServerUrlSettings
import com.markrai.vaultist.data.settings.ThemePreferences
import com.markrai.vaultist.di.config.NetworkConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindVaultRepository(repository: DefaultVaultRepository): VaultRepository

    @Binds
    @Singleton
    abstract fun bindAskPreferences(preferences: DataStoreAskPreferences): AskPreferences

    @Binds
    @Singleton
    abstract fun bindThemePreferences(preferences: DataStoreThemePreferences): ThemePreferences

    @Binds
    @Singleton
    abstract fun bindBrowseSortPreferences(preferences: DataStoreBrowseSortPreferences): BrowseSortPreferences

    @Binds
    @Singleton
    abstract fun bindBrowseViewPreferences(preferences: DataStoreBrowseViewPreferences): BrowseViewPreferences

    @Binds
    @Singleton
    abstract fun bindModifiedDatePreferences(preferences: DataStoreModifiedDatePreferences): ModifiedDatePreferences

    @Binds
    @Singleton
    abstract fun bindDateTimeInsertFormatter(formatter: DefaultDateTimeInsertFormatter): DateTimeInsertFormatter

    @Binds
    @Singleton
    abstract fun bindServerUrlSettings(settings: ServerSettings): ServerUrlSettings
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(networkConfig: NetworkConfig): OkHttpClient = networkConfig.toOkHttpClient()
}
