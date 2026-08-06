package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.settings.BrowseViewPreferences
import com.markrai.vaultist.domain.BrowseViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBrowseViewPreferences(
    initialViewMode: BrowseViewMode = BrowseViewMode.Stacked,
) : BrowseViewPreferences {
    private val _viewMode = MutableStateFlow(initialViewMode)
    override val viewMode: Flow<BrowseViewMode> = _viewMode.asStateFlow()

    override suspend fun setViewMode(mode: BrowseViewMode) {
        _viewMode.value = mode
    }
}
