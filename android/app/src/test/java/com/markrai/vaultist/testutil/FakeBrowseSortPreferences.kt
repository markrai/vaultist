package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.settings.BrowseSortPreferences
import com.markrai.vaultist.domain.BrowseSortMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FakeBrowseSortPreferences(
    initialSortMode: BrowseSortMode = BrowseSortMode.Alphabetical,
    private val restoreDelayMs: Long = 0L,
) : BrowseSortPreferences {
    private val _sortMode = MutableStateFlow(initialSortMode)
    override val sortMode: Flow<BrowseSortMode> =
        if (restoreDelayMs <= 0L) {
            _sortMode.asStateFlow()
        } else {
            flow {
                delay(restoreDelayMs)
                emitAll(_sortMode)
            }
        }

    override suspend fun setSortMode(mode: BrowseSortMode) {
        _sortMode.value = mode
    }
}
