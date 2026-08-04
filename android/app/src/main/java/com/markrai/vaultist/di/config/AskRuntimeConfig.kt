package com.markrai.vaultist.di.config

import java.time.Duration

data class AskRuntimeConfig(
    val topCandidates: Int = 8,
    val maxNoteFetchConcurrency: Int = 3,
    val maxSearchConcurrency: Int = 4,
    val safetyMarginTokens: Int = 96,
    val maxOutputTokens: Int = 256,
    val inputBudgetCap: Int = 4000,
    val defaultTokenLimit: Int = 4096,
    val charsPerTokenEstimate: Int = 4,
    val maxTokenTrimIterations: Int = 6,
    val askTimeout: Duration = Duration.ofSeconds(45),
    val modelStatusTimeout: Duration = Duration.ofSeconds(12),
    val readyPollAttempts: Int = 12,
    val readyPollDelay: Duration = Duration.ofMillis(500),
)
