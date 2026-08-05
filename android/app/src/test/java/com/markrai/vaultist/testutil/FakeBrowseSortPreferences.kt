package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.settings.BrowseSortPreferences
import com.markrai.vaultist.domain.BrowseSortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBrowseSortPreferences(
    initialSortMode: BrowseSortMode = BrowseSortMode.Alphabetical,
) : BrowseSortPreferences {
    private val _sortMode = MutableStateFlow(initialSortMode)
    override val sortMode: Flow<BrowseSortMode> = _sortMode.asStateFlow()

    override suspend fun setSortMode(mode: BrowseSortMode) {
        _sortMode.value = mode
    }
}
