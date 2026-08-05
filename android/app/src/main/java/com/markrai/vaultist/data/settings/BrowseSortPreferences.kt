package com.markrai.vaultist.data.settings

import com.markrai.vaultist.domain.BrowseSortMode
import kotlinx.coroutines.flow.Flow

interface BrowseSortPreferences {
    val sortMode: Flow<BrowseSortMode>
    suspend fun setSortMode(mode: BrowseSortMode)
}
