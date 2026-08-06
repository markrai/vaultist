package com.markrai.vaultist.data.widget

import android.content.Context
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.markrai.vaultist.ui.widget.NoteWidget
import com.markrai.vaultist.ui.widget.WidgetNoteState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class NoteWidgetRefresh @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: NoteWidgetStore,
) : NoteWidgetRefresher {
    private val widget = NoteWidget()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun refreshAll() {
        widget.updateAll(context)
    }

    override fun scheduleRefreshWidget(appWidgetId: Int, noteId: String) {
        refreshScope.launch {
            refreshWidget(appWidgetId, noteId)
        }
    }

    @Suppress("RestrictedApi")
    override suspend fun refreshWidget(appWidgetId: Int, noteId: String?): Boolean {
        val resolvedNoteId = noteId?.takeIf { it.isNotBlank() }
            ?: preferences.getNoteId(appWidgetId)
            ?: return false

        preferences.setBinding(appWidgetId, resolvedNoteId)
        // AppWidgetId is the stable GlanceId for a platform widget id; avoid getGlanceIdBy
        // failing when AppWidgetInfo is briefly unavailable during configure.
        val glanceId = AppWidgetId(appWidgetId)
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetNoteState.noteIdKey] = resolvedNoteId
            prefs[WidgetNoteState.refreshTokenKey] = System.currentTimeMillis()
        }
        widget.update(context, glanceId)
        return true
    }

    override suspend fun refreshForNote(noteId: String) {
        preferences.findAppWidgetIdsForNote(noteId).forEach { appWidgetId ->
            refreshWidget(appWidgetId, noteId)
        }
    }

    override suspend fun clearAllAndRefresh() {
        preferences.removeAll()
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(NoteWidget::class.java).forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs.remove(WidgetNoteState.noteIdKey)
                prefs.remove(WidgetNoteState.refreshTokenKey)
            }
            widget.update(context, glanceId)
        }
    }
}
