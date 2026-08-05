package com.markrai.vaultist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.markrai.vaultist.domain.BrowseSortMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreBrowseSortPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : BrowseSortPreferences {
    private val sortModeKey = stringPreferencesKey("browse_sort_mode")

    override val sortMode: Flow<BrowseSortMode> = context.vaultistDataStore.data.map { prefs ->
        BrowseSortMode.fromId(prefs[sortModeKey])
    }

    override suspend fun setSortMode(mode: BrowseSortMode) {
        context.vaultistDataStore.edit { it[sortModeKey] = mode.id }
    }
}
