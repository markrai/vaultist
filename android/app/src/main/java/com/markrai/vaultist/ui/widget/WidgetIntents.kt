package com.markrai.vaultist.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.markrai.vaultist.MainActivity

object WidgetIntents {
    const val EXTRA_NOTE_ID = "com.markrai.vaultist.extra.NOTE_ID"

    fun openNote(context: Context, noteId: String, appWidgetId: Int): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("vaultist://widget/note/${Uri.encode(noteId)}?widgetId=$appWidgetId")
            putExtra(EXTRA_NOTE_ID, noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    fun extractNoteId(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_NOTE_ID)?.takeIf { it.isNotBlank() }
}
