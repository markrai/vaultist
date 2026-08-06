package com.markrai.vaultist.data.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.markrai.vaultist.ui.widget.NoteWidget
import com.markrai.vaultist.ui.widget.NoteWidgetReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteWidgetRefresh @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: NoteWidgetStore,
) : NoteWidgetRefresher {
    private val widget = NoteWidget()

    override suspend fun refreshAll() {
        widget.updateAll(context)
    }

    @Suppress("RestrictedApi")
    override suspend fun refreshWidget(appWidgetId: Int) {
        widget.update(context, AppWidgetId(appWidgetId))
        sendUpdateBroadcast(intArrayOf(appWidgetId))
    }

    @Suppress("RestrictedApi")
    override suspend fun refreshForNote(noteId: String) {
        val widgetIds = preferences.findAppWidgetIdsForNote(noteId)
        widgetIds.forEach { appWidgetId ->
            widget.update(context, AppWidgetId(appWidgetId))
        }
        sendUpdateBroadcast(widgetIds.toIntArray())
    }

    override suspend fun clearAllAndRefresh() {
        preferences.removeAll()
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(NoteWidget::class.java).forEach { glanceId ->
            widget.update(context, glanceId)
        }
    }

    private fun sendUpdateBroadcast(appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            component = ComponentName(context, NoteWidgetReceiver::class.java)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        context.sendBroadcast(intent)
    }
}
