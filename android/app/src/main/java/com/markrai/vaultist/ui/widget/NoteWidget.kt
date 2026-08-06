package com.markrai.vaultist.ui.widget

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.markrai.vaultist.data.widget.NoteWidgetLoadResult
import dagger.hilt.android.EntryPointAccessors

class NoteWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val appWidgetId = GlanceAppWidgetManager(appContext).getAppWidgetId(id)
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val loader = entryPoint.noteWidgetLoader()
        val config = entryPoint.noteWidgetConfig()
        val seededNoteId = getAppWidgetState(appContext, PreferencesGlanceStateDefinition, id)
            .let { prefs -> prefs[WidgetNoteState.noteIdKey] }

        // Seed for the first frame. Subsequent configure/app updates may arrive while a Glance
        // session is still running; those do not re-enter provideGlance, so content must reload
        // from currentState() inside the composition.
        val initialResult = loader.load(appWidgetId, seededNoteId)
        val initialContent = (initialResult as? NoteWidgetLoadResult.Content)?.let { loaded ->
            WidgetMarkdownMapper.map(loaded.note, config)
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val glanceNoteId = prefs[WidgetNoteState.noteIdKey]
            val refreshToken = prefs[WidgetNoteState.refreshTokenKey]

            var loadResult by remember { mutableStateOf(initialResult) }
            var content by remember { mutableStateOf(initialContent) }

            LaunchedEffect(glanceNoteId, refreshToken) {
                val result = loader.load(appWidgetId, glanceNoteId)
                loadResult = result
                content = (result as? NoteWidgetLoadResult.Content)?.let { loaded ->
                    WidgetMarkdownMapper.map(loaded.note, config)
                }
            }

            val noteId = (loadResult as? NoteWidgetLoadResult.Content)?.note?.id ?: glanceNoteId

            GlanceTheme {
                NoteWidgetRoot(
                    loadResult = loadResult,
                    content = content,
                    noteId = noteId,
                    appWidgetId = appWidgetId,
                )
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context.applicationContext).getAppWidgetId(glanceId)
        val prefs = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .noteWidgetStore()
        prefs.removeBinding(appWidgetId)
        updateAppWidgetState(context, glanceId) { preferences ->
            preferences.remove(WidgetNoteState.noteIdKey)
            preferences.remove(WidgetNoteState.refreshTokenKey)
        }
    }
}
