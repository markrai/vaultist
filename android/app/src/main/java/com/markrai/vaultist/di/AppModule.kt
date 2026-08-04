package com.markrai.vaultist.di

import com.markrai.vaultist.data.repository.DefaultVaultRepository
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.data.settings.AskPreferences
import com.markrai.vaultist.data.settings.DataStoreAskPreferences
import com.markrai.vaultist.data.settings.ServerSettings
import com.markrai.vaultist.data.settings.ServerUrlSettings
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
    abstract fun bindServerUrlSettings(settings: ServerSettings): ServerUrlSettings
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(networkConfig: NetworkConfig): OkHttpClient = networkConfig.toOkHttpClient()
}
