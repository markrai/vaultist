package com.markrai.vaultist.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markrai.vaultist.data.ask.AskOutcome
import com.markrai.vaultist.data.ask.AskStage
import com.markrai.vaultist.data.ask.VaultAskEngine
import com.markrai.vaultist.data.genai.LocalAiCapability
import com.markrai.vaultist.data.genai.PromptGenerationClient
import com.markrai.vaultist.di.AskDispatcher
import com.markrai.vaultist.domain.BrowseItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AskUiState(
    val submittedQuestion: String? = null,
    val askAnswer: String? = null,
    val askSources: List<BrowseItem> = emptyList(),
    val askStage: AskStage? = null,
    val askCapability: LocalAiCapability = LocalAiCapability.Unchecked,
    val askSubmitting: Boolean = false,
    val askHadInvalidCitations: Boolean = false,
    val askMessage: String? = null,
    val searching: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AskViewModel @Inject constructor(
    private val askEngine: VaultAskEngine,
    private val promptClient: PromptGenerationClient,
    @AskDispatcher private val askWorkDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(AskUiState())
    val state: StateFlow<AskUiState> = _state

    private var askJob: Job? = null
    private var capabilityJob: Job? = null
    private var askRequestId = 0L
    private var active = false

    fun onEnteredAsk() {
        active = true
        recheckCapability()
    }

    fun onLeftAsk() {
        active = false
        invalidate()
        clearResults()
    }

    fun onResumed() {
        if (!active) return
        when (_state.value.askCapability) {
            is LocalAiCapability.Ready,
            LocalAiCapability.Checking,
            is LocalAiCapability.Downloading,
            -> Unit
            else -> recheckCapability()
        }
    }

    fun retry(question: String) {
        when {
            needsCapabilityAction() -> recheckCapability()
            _state.value.submittedQuestion != null -> submit(question)
        }
    }

    fun submit(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _state.value.askSubmitting) return

        val requestId = ++askRequestId
        askJob?.cancel()
        askJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    askSubmitting = true,
                    searching = true,
                    submittedQuestion = trimmed,
                    askAnswer = null,
                    askSources = emptyList(),
                    askStage = AskStage.CheckingOnDeviceAi,
                    askHadInvalidCitations = false,
                    askMessage = null,
                    error = null,
                )
            }

            val outcome = withContext(askWorkDispatcher) {
                val capability = ensureCapabilityReady()
                if (requestId != askRequestId) return@withContext AskOutcome.Cancelled
                if (capability !is LocalAiCapability.Ready) {
                    return@withContext AskOutcome.Failure(capabilityMessage(capability), retryable = true)
                }

                askEngine.ask(
                    question = trimmed,
                    requestId = requestId,
                    isActive = { requestId == askRequestId },
                    onStage = { stage ->
                        if (requestId == askRequestId) {
                            _state.update {
                                it.copy(
                                    askStage = stage,
                                    searching = stage == AskStage.SearchingHost || stage == AskStage.LoadingNotes,
                                )
                            }
                        }
                    },
                )
            }

            if (requestId != askRequestId) return@launch
            publishAskOutcome(outcome)
        }
    }

    fun cancel() {
        invalidate()
        _state.update {
            it.copy(
                askSubmitting = false,
                askStage = null,
                searching = false,
            )
        }
    }

    fun recheckCapability() {
        if (capabilityJob?.isActive == true) return
        capabilityJob = viewModelScope.launch {
            _state.update { it.copy(askCapability = LocalAiCapability.Checking) }
            val capability = promptClient.checkCapability()
            if (!active) return@launch
            when (capability) {
                LocalAiCapability.Downloadable -> {
                    // Skip the manual Download click: Ask mode implies consent to fetch the model.
                    _state.update { it.copy(askCapability = LocalAiCapability.Downloading()) }
                    val afterDownload = promptClient.downloadModel()
                    if (!active) return@launch
                    _state.update { it.copy(askCapability = afterDownload) }
                }
                else -> _state.update { it.copy(askCapability = capability) }
            }
        }
    }

    fun downloadAskModel() {
        if (capabilityJob?.isActive == true &&
            _state.value.askCapability is LocalAiCapability.Downloading
        ) {
            return
        }
        capabilityJob?.cancel()
        capabilityJob = viewModelScope.launch {
            _state.update { it.copy(askCapability = LocalAiCapability.Downloading()) }
            val capability = promptClient.downloadModel()
            if (!active) return@launch
            _state.update { it.copy(askCapability = capability) }
        }
    }

    fun clearResults() {
        invalidate()
        _state.update {
            it.copy(
                submittedQuestion = null,
                askAnswer = null,
                askSources = emptyList(),
                askStage = null,
                askSubmitting = false,
                askHadInvalidCitations = false,
                askMessage = null,
                searching = false,
                error = null,
            )
        }
    }

    fun invalidate() {
        askRequestId += 1
        askJob?.cancel()
    }

    private suspend fun ensureCapabilityReady(): LocalAiCapability {
        val current = _state.value.askCapability
        if (current is LocalAiCapability.Ready) return current
        val checked = promptClient.checkCapability()
        if (active) {
            _state.update { it.copy(askCapability = checked) }
        }
        return checked
    }

    private fun publishAskOutcome(outcome: AskOutcome) {
        when (outcome) {
            is AskOutcome.Success -> _state.update {
                it.copy(
                    askSubmitting = false,
                    searching = false,
                    askStage = null,
                    askAnswer = outcome.answer,
                    askSources = outcome.sources,
                    askHadInvalidCitations = outcome.hadInvalidCitations,
                    askMessage = null,
                    error = null,
                )
            }
            is AskOutcome.NoMatches -> _state.update {
                it.copy(
                    askSubmitting = false,
                    searching = false,
                    askStage = null,
                    askAnswer = null,
                    askSources = emptyList(),
                    askMessage = outcome.message,
                    error = null,
                )
            }
            is AskOutcome.Partial -> _state.update {
                it.copy(
                    askSubmitting = false,
                    searching = false,
                    askStage = null,
                    askAnswer = null,
                    askSources = outcome.sources,
                    askMessage = outcome.message,
                    error = null,
                )
            }
            is AskOutcome.Failure -> _state.update {
                it.copy(
                    askSubmitting = false,
                    searching = false,
                    askStage = null,
                    askAnswer = null,
                    askSources = emptyList(),
                    askMessage = null,
                    error = outcome.message,
                )
            }
            AskOutcome.Cancelled -> _state.update {
                it.copy(
                    askSubmitting = false,
                    searching = false,
                    askStage = null,
                )
            }
        }
    }

    private fun needsCapabilityAction(): Boolean {
        return when (_state.value.askCapability) {
            is LocalAiCapability.Downloadable,
            is LocalAiCapability.Failed,
            LocalAiCapability.Unavailable,
            LocalAiCapability.Unchecked,
            -> true
            else -> false
        }
    }

    private fun capabilityMessage(capability: LocalAiCapability): String = when (capability) {
        LocalAiCapability.Unavailable,
        LocalAiCapability.Unchecked,
        is LocalAiCapability.Failed,
        -> "On-device AI is currently unavailable."
        LocalAiCapability.Downloadable -> "On-device AI needs a one-time model download."
        is LocalAiCapability.Downloading -> "Downloading on-device AI model…"
        LocalAiCapability.Checking -> "Checking on-device AI…"
        is LocalAiCapability.Ready -> ""
    }
}
