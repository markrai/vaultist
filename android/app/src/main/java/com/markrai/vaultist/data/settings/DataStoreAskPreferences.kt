package com.markrai.vaultist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreAskPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : AskPreferences {
    private val enableAskThinkingKey = booleanPreferencesKey("enable_ask_thinking")

    override val enableAskThinking: Flow<Boolean> = context.vaultistDataStore.data.map {
        it[enableAskThinkingKey] ?: false
    }

    override suspend fun setEnableAskThinking(enabled: Boolean) {
        context.vaultistDataStore.edit { it[enableAskThinkingKey] = enabled }
    }
}
