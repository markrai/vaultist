package com.markrai.vaultist.data.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.vaultistDataStore by preferencesDataStore(name = "vaultist_settings")
