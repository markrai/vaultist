package com.markrai.vaultist.data.genai

sealed interface LocalAiCapability {
    data object Unchecked : LocalAiCapability
    data object Checking : LocalAiCapability
    data class Ready(val tokenLimit: Int) : LocalAiCapability
    data object Downloadable : LocalAiCapability
    data class Downloading(val progressBytes: Long? = null) : LocalAiCapability
    data object Unavailable : LocalAiCapability
    data class Failed(val reason: String, val retryable: Boolean) : LocalAiCapability
}

enum class PromptFailureKind {
    NotReady,
    Busy,
    Quota,
    BackgroundBlocked,
    EmptyResponse,
    Cancelled,
    Other,
}

sealed interface PromptGenerationResult {
    data class Success(val text: String) : PromptGenerationResult
    data class Failure(val kind: PromptFailureKind, val message: String) : PromptGenerationResult
}
