package com.markrai.vaultist.ui.widget

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object WidgetNoteState {
    val noteIdKey = stringPreferencesKey("note_id")
    /** Bumped on every refresh so a running Glance session always recomposes/reloads. */
    val refreshTokenKey = longPreferencesKey("refresh_token")
}
