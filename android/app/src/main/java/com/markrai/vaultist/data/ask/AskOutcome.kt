package com.markrai.vaultist.data.ask

import com.markrai.vaultist.domain.BrowseItem

enum class AskStage {
    CheckingOnDeviceAi,
    SearchingHost,
    LoadingNotes,
    AnsweringOnDevice,
}

sealed interface AskOutcome {
    data class Success(
        val answer: String,
        val sources: List<BrowseItem>,
        val hadInvalidCitations: Boolean,
    ) : AskOutcome

    data class NoMatches(val message: String = "No matching notes found.") : AskOutcome

    data class Partial(
        val message: String,
        val sources: List<BrowseItem>,
    ) : AskOutcome

    data class Failure(val message: String, val retryable: Boolean = false) : AskOutcome

    data object Cancelled : AskOutcome
}
