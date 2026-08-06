package com.markrai.vaultist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.markrai.vaultist.domain.BrowseViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreBrowseViewPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : BrowseViewPreferences {
    private val viewModeKey = stringPreferencesKey("browse_view_mode")

    override val viewMode: Flow<BrowseViewMode> = context.vaultistDataStore.data.map { prefs ->
        BrowseViewMode.fromId(prefs[viewModeKey])
    }

    override suspend fun setViewMode(mode: BrowseViewMode) {
        context.vaultistDataStore.edit { it[viewModeKey] = mode.id }
    }
}
