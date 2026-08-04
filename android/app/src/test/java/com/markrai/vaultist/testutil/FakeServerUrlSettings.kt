package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.settings.ServerUrlSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeServerUrlSettings(
    initial: String? = "http://localhost",
) : ServerUrlSettings {
    private val url = MutableStateFlow(initial)
    override val serverUrl: Flow<String?> = url

    override suspend fun saveServerUrl(value: String) {
        url.value = value
    }

    fun set(value: String?) {
        url.value = value
    }
}
