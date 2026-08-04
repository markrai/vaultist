package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.settings.AskPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAskPreferences(
    enableThinking: Boolean = false,
) : AskPreferences {
    private val thinking = MutableStateFlow(enableThinking)
    override val enableAskThinking: Flow<Boolean> = thinking

    override suspend fun setEnableAskThinking(enabled: Boolean) {
        thinking.value = enabled
    }
}
