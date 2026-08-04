package com.vaultview.di

import com.vaultview.data.genai.AicorePromptClient
import com.vaultview.data.genai.PromptGenerationClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GenAiModule {
    @Binds
    @Singleton
    abstract fun bindPromptGenerationClient(client: AicorePromptClient): PromptGenerationClient
}
