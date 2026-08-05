package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.settings.ModifiedDatePreferences
import com.markrai.vaultist.domain.ModifiedDateStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeModifiedDatePreferences(
    initialStyle: ModifiedDateStyle = ModifiedDateStyle.Absolute,
) : ModifiedDatePreferences {
    private val _style = MutableStateFlow(initialStyle)
    override val style: Flow<ModifiedDateStyle> = _style.asStateFlow()

    override suspend fun setStyle(style: ModifiedDateStyle) {
        _style.value = style
    }
}
