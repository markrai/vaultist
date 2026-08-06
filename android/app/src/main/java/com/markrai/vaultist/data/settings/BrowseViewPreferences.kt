package com.markrai.vaultist.data.settings

import com.markrai.vaultist.domain.BrowseViewMode
import kotlinx.coroutines.flow.Flow

interface BrowseViewPreferences {
    val viewMode: Flow<BrowseViewMode>
    suspend fun setViewMode(mode: BrowseViewMode)
}
