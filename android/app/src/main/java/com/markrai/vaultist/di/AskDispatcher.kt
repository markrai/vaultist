package com.markrai.vaultist.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AskDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AskDispatcherModule {
    @Provides
    @AskDispatcher
    fun provideAskDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
