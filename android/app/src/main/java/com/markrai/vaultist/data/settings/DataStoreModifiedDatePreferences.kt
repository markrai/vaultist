package com.markrai.vaultist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.markrai.vaultist.domain.ModifiedDateStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreModifiedDatePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : ModifiedDatePreferences {
    private val styleKey = stringPreferencesKey("modified_date_style")

    override val style: Flow<ModifiedDateStyle> = context.vaultistDataStore.data.map { prefs ->
        ModifiedDateStyle.fromId(prefs[styleKey])
    }

    override suspend fun setStyle(style: ModifiedDateStyle) {
        context.vaultistDataStore.edit { it[styleKey] = style.id }
    }
}
