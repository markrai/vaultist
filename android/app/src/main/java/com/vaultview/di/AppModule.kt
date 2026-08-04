package com.vaultview.di

import com.vaultview.data.repository.DefaultVaultRepository
import com.vaultview.data.repository.VaultRepository
import com.vaultview.data.settings.AskPreferences
import com.vaultview.data.settings.ServerSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
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
    abstract fun bindAskPreferences(settings: ServerSettings): AskPreferences
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
}
