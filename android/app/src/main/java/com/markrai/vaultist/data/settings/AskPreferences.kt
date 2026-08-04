package com.markrai.vaultist.data.settings

import kotlinx.coroutines.flow.Flow

interface AskPreferences {
    val enableAskThinking: Flow<Boolean>
    suspend fun setEnableAskThinking(enabled: Boolean)
}
