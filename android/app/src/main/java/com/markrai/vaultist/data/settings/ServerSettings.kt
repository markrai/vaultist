package com.markrai.vaultist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ServerUrlSettings {
    val serverUrl: Flow<String?>
    suspend fun saveServerUrl(url: String)
}

@Singleton
class ServerSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : ServerUrlSettings {
    private val serverUrlKey = stringPreferencesKey("server_url")

    override val serverUrl: Flow<String?> = context.vaultistDataStore.data.map { it[serverUrlKey] }

    override suspend fun saveServerUrl(url: String) {
        context.vaultistDataStore.edit { it[serverUrlKey] = url }
    }
}
