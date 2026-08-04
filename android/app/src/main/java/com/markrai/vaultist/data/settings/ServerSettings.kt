package com.markrai.vaultist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vaultistDataStore by preferencesDataStore(name = "vaultist_settings")

@Singleton
class ServerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : AskPreferences {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val enableAskThinkingKey = booleanPreferencesKey("enable_ask_thinking")

    val serverUrl: Flow<String?> = context.vaultistDataStore.data.map { it[serverUrlKey] }

    override val enableAskThinking: Flow<Boolean> = context.vaultistDataStore.data.map {
        it[enableAskThinkingKey] ?: false
    }

    suspend fun saveServerUrl(url: String) {
        context.vaultistDataStore.edit { it[serverUrlKey] = url }
    }

    override suspend fun setEnableAskThinking(enabled: Boolean) {
        context.vaultistDataStore.edit { it[enableAskThinkingKey] = enabled }
    }
}
