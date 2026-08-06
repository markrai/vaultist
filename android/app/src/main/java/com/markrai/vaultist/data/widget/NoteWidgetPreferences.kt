package com.markrai.vaultist.data.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetDataStore by preferencesDataStore(name = "vaultist_widget_preferences")

@Singleton
class NoteWidgetPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : NoteWidgetStore {
    private val dataStore: DataStore<Preferences> = context.widgetDataStore

    override suspend fun getNoteId(appWidgetId: Int): String? {
        val key = noteIdKey(appWidgetId)
        return dataStore.data.map { prefs -> prefs[key] }.first()
    }

    override suspend fun setBinding(appWidgetId: Int, noteId: String) {
        dataStore.edit { prefs ->
            val ids = prefs[widgetIdsKey]?.toMutableSet() ?: mutableSetOf()
            ids += appWidgetId.toString()
            prefs[widgetIdsKey] = ids
            prefs[noteIdKey(appWidgetId)] = noteId
        }
    }

    override suspend fun removeBinding(appWidgetId: Int) {
        dataStore.edit { prefs ->
            val ids = prefs[widgetIdsKey]?.toMutableSet() ?: mutableSetOf()
            ids.remove(appWidgetId.toString())
            if (ids.isEmpty()) {
                prefs.remove(widgetIdsKey)
            } else {
                prefs[widgetIdsKey] = ids
            }
            prefs.remove(noteIdKey(appWidgetId))
        }
    }

    override suspend fun findAppWidgetIdsForNote(noteId: String): List<Int> {
        val prefs = dataStore.data.first()
        val widgetIds = prefs[widgetIdsKey].orEmpty()
        return widgetIds.mapNotNull { id ->
            id.toIntOrNull()?.takeIf { widgetId ->
                prefs[noteIdKey(widgetId)] == noteId
            }
        }
    }

    override suspend fun removeAll() {
        dataStore.edit { prefs ->
            val widgetIds = prefs[widgetIdsKey].orEmpty()
            widgetIds.forEach { id ->
                id.toIntOrNull()?.let { widgetId -> prefs.remove(noteIdKey(widgetId)) }
            }
            prefs.remove(widgetIdsKey)
        }
    }

    private companion object {
        val widgetIdsKey = stringSetPreferencesKey("note_widget_ids")

        fun noteIdKey(appWidgetId: Int) = stringPreferencesKey("note_widget_${appWidgetId}_note_id")
    }
}
