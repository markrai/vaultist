package com.markrai.vaultist.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import com.markrai.vaultist.data.widget.NoteWidgetLoadResult
import dagger.hilt.android.EntryPointAccessors

class NoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val appWidgetId = GlanceAppWidgetManager(appContext).getAppWidgetId(id)
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val loadResult = entryPoint.noteWidgetLoader().load(appWidgetId)
        val mappedContent = (loadResult as? NoteWidgetLoadResult.Content)?.let { loaded ->
            WidgetMarkdownMapper.map(loaded.note, entryPoint.noteWidgetConfig())
        }
        val noteId = (loadResult as? NoteWidgetLoadResult.Content)?.note?.id

        provideContent {
            GlanceTheme {
                NoteWidgetRoot(
                    loadResult = loadResult,
                    content = mappedContent,
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
    }
}
