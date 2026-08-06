package com.markrai.vaultist.data.settings

import com.markrai.vaultist.domain.DateTimeInsertFormat
import kotlinx.coroutines.flow.Flow

interface DateTimeInsertPreferences {
    val format: Flow<DateTimeInsertFormat>
    suspend fun setFormat(format: DateTimeInsertFormat)
}
