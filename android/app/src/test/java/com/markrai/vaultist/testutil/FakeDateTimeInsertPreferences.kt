package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.settings.DateTimeInsertPreferences
import com.markrai.vaultist.domain.DateTimeInsertFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeDateTimeInsertPreferences(
    initialFormat: DateTimeInsertFormat = DateTimeInsertFormat.IsoDateTime,
) : DateTimeInsertPreferences {
    private val _format = MutableStateFlow(initialFormat)
    override val format: Flow<DateTimeInsertFormat> = _format.asStateFlow()

    override suspend fun setFormat(format: DateTimeInsertFormat) {
        _format.value = format
    }
}
