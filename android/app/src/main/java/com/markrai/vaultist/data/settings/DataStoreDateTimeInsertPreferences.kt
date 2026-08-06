package com.markrai.vaultist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.markrai.vaultist.domain.DateTimeInsertFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreDateTimeInsertPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) : DateTimeInsertPreferences {
    private val formatKey = stringPreferencesKey("datetime_insert_format")

    override val format: Flow<DateTimeInsertFormat> = context.vaultistDataStore.data.map { prefs ->
        DateTimeInsertFormat.fromId(prefs[formatKey])
    }

    override suspend fun setFormat(format: DateTimeInsertFormat) {
        context.vaultistDataStore.edit { it[formatKey] = format.id }
    }
}
