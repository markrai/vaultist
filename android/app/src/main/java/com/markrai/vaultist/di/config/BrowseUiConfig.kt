package com.markrai.vaultist.di.config

data class BrowseUiConfig(
    val debounceMs: Long = 300,
    val indexPollAttempts: Int = 30,
    val indexPollDelayMs: Long = 500,
)
