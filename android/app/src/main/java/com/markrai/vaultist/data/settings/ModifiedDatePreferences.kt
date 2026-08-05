package com.markrai.vaultist.data.settings

import com.markrai.vaultist.domain.ModifiedDateStyle
import kotlinx.coroutines.flow.Flow

interface ModifiedDatePreferences {
    val style: Flow<ModifiedDateStyle>
    suspend fun setStyle(style: ModifiedDateStyle)
}
