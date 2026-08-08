package com.markrai.vaultist.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = parameters[appWidgetIdKey] ?: return
        val sourceLine = parameters[sourceLineKey] ?: return
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val noteId = entryPoint.noteWidgetStore().getNoteId(appWidgetId) ?: return
        entryPoint.vaultRepository().let { repository ->
            toggleWidgetTask(repository, noteId, sourceLine)
        }
        entryPoint.noteWidgetRefresher().refreshForNote(noteId)
    }

    companion object {
        val appWidgetIdKey = ActionParameters.Key<Int>("appWidgetId")
        val sourceLineKey = ActionParameters.Key<Int>("sourceLine")
    }
}
