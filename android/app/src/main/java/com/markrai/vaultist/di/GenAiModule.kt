package com.markrai.vaultist.di

import com.markrai.vaultist.data.genai.AicorePromptClient
import com.markrai.vaultist.data.genai.BuildConfigOnDeviceAskEnabled
import com.markrai.vaultist.data.genai.OnDeviceAskEnabled
import com.markrai.vaultist.data.genai.PromptGenerationClient
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

    @Binds
    @Singleton
    abstract fun bindOnDeviceAskEnabled(enabled: BuildConfigOnDeviceAskEnabled): OnDeviceAskEnabled
}
