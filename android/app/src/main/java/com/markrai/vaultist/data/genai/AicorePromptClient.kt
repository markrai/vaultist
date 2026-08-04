package com.markrai.vaultist.data.genai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.markrai.vaultist.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Prompt API client. Status/download/generate are dispatched to [Dispatchers.Main]
 * because AICore binder calls can hang indefinitely off the main thread.
 */
@Singleton
class AicorePromptClient @Inject constructor() : PromptGenerationClient {
    private val mutex = Mutex()
    private var model = lazy { Generation.getClient() }
    private var closed = false
    private var cachedTokenLimit: Int? = null

    override suspend fun checkCapability(): LocalAiCapability {
        if (!BuildConfig.ENABLE_ON_DEVICE_ASK) {
            return LocalAiCapability.Unavailable
        }
        return readStatus() ?: LocalAiCapability.Failed(
            "On-device AI check timed out. Tap Retry.",
            retryable = true,
        )
    }

    override suspend fun downloadModel(): LocalAiCapability {
        if (!BuildConfig.ENABLE_ON_DEVICE_ASK) {
            return LocalAiCapability.Unavailable
        }
        return try {
            var failed = false
            var completed = false
            withContext(Dispatchers.Main) {
                client().download()
                    .catch { error ->
                        if (error is CancellationException) throw error
                        failed = true
                        Log.w(TAG, "Prompt API download failed", error)
                    }
                    .collect { status ->
                        when (status) {
                            is DownloadStatus.DownloadFailed -> failed = true
                            is DownloadStatus.DownloadCompleted -> completed = true
                            else -> Unit
                        }
                    }
            }
            if (failed) {
                return LocalAiCapability.Failed("Model download failed.", retryable = true)
            }
            // Status often lags briefly after DownloadCompleted; poll instead of one flaky check.
            awaitReadyAfterDownload(downloadReportedComplete = completed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Prompt API download request failed", error)
            LocalAiCapability.Failed("Model download failed.", retryable = true)
        }
    }

    override suspend fun getTokenLimit(): Int? {
        cachedTokenLimit?.let { return it }
        return withTimeoutOrNull(STATUS_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                runCatching {
                    client().getTokenLimit().also { cachedTokenLimit = it }
                }.getOrNull()
            }
        }
    }

    override suspend fun countTokens(request: PromptRequest): Int? = withTimeoutOrNull(STATUS_TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            runCatching {
                client().countTokens(buildRequest(request)).totalTokens
            }.getOrNull()
        }
    }

    override suspend fun generate(request: PromptRequest): PromptGenerationResult {
        if (!BuildConfig.ENABLE_ON_DEVICE_ASK) {
            return PromptGenerationResult.Failure(
                PromptFailureKind.NotReady,
                "On-device AI is currently unavailable.",
            )
        }
        return try {
            withContext(Dispatchers.Main) {
                if (client().checkStatus() != FeatureStatus.AVAILABLE) {
                    return@withContext PromptGenerationResult.Failure(
                        PromptFailureKind.NotReady,
                        "On-device AI is currently unavailable.",
                    )
                }
                val response = client().generateContent(buildRequest(request))
                val text = response.candidates.firstOrNull()?.text?.trim().orEmpty()
                if (text.isBlank()) {
                    PromptGenerationResult.Failure(
                        PromptFailureKind.EmptyResponse,
                        "On-device AI returned an empty answer.",
                    )
                } else {
                    PromptGenerationResult.Success(text)
                }
            }
        } catch (cancelled: CancellationException) {
            PromptGenerationResult.Failure(PromptFailureKind.Cancelled, "Ask was cancelled.")
        } catch (error: Exception) {
            Log.w(TAG, "Prompt API generation failed", error)
            PromptGenerationResult.Failure(mapFailureKind(error), mapFailureMessage(error))
        }
    }

    override fun close() {
        closed = true
        if (model.isInitialized()) {
            runCatching { model.value.close() }
        }
        model = lazy { Generation.getClient() }
        cachedTokenLimit = null
    }

    private suspend fun awaitReadyAfterDownload(downloadReportedComplete: Boolean): LocalAiCapability {
        repeat(READY_POLL_ATTEMPTS) { attempt ->
            when (val status = readStatus()) {
                is LocalAiCapability.Ready -> return status
                is LocalAiCapability.Downloading -> delay(READY_POLL_DELAY_MS)
                LocalAiCapability.Downloadable -> {
                    // Still settling, or download didn't actually start.
                    if (attempt < READY_POLL_ATTEMPTS - 1) delay(READY_POLL_DELAY_MS)
                }
                LocalAiCapability.Unavailable,
                is LocalAiCapability.Failed,
                -> {
                    // Transient UNAVAILABLE right after install is common; keep waiting.
                    if (attempt < READY_POLL_ATTEMPTS - 1) {
                        delay(READY_POLL_DELAY_MS)
                    } else {
                        return status
                    }
                }
                LocalAiCapability.Checking,
                LocalAiCapability.Unchecked,
                null,
                -> delay(READY_POLL_DELAY_MS)
            }
        }
        // Download finished from AICore's point of view; allow Ask to proceed.
        if (downloadReportedComplete) {
            Log.i(TAG, "Download completed; treating as Ready while status settles")
            return LocalAiCapability.Ready(cachedTokenLimit ?: DEFAULT_TOKEN_LIMIT)
        }
        // Last observed status may still be settling; prefer a soft retryable failure.
        return when (val last = readStatus()) {
            is LocalAiCapability.Ready -> last
            else -> LocalAiCapability.Failed(
                "On-device AI is still finishing setup. Tap Retry in a moment.",
                retryable = true,
            )
        }
    }

    private suspend fun readStatus(): LocalAiCapability? = withTimeoutOrNull(STATUS_TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            try {
                when (client().checkStatus()) {
                    FeatureStatus.AVAILABLE ->
                        LocalAiCapability.Ready(cachedTokenLimit ?: DEFAULT_TOKEN_LIMIT)
                    FeatureStatus.DOWNLOADABLE -> LocalAiCapability.Downloadable
                    FeatureStatus.DOWNLOADING -> LocalAiCapability.Downloading()
                    FeatureStatus.UNAVAILABLE -> LocalAiCapability.Unavailable
                    else -> LocalAiCapability.Failed(
                        "On-device AI is temporarily unavailable.",
                        retryable = true,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Prompt API status check failed", error)
                LocalAiCapability.Failed("On-device AI is currently unavailable.", retryable = true)
            }
        }
    }

    private suspend fun client() = mutex.withLock {
        check(!closed) { "Prompt client is closed" }
        model.value
    }

    private fun buildRequest(request: PromptRequest) = generateContentRequest(
        TextPart(request.userText),
    ) {
        systemInstruction = SystemInstruction(request.systemInstruction)
        maxOutputTokens = request.maxOutputTokens
        temperature = request.temperature
        candidateCount = 1
        enableThinking = request.enableThinking
    }

    private fun mapFailureKind(error: Throwable): PromptFailureKind {
        val normalized = error.message.orEmpty().lowercase()
        return when {
            "background_use_blocked" in normalized -> PromptFailureKind.BackgroundBlocked
            "busy" in normalized -> PromptFailureKind.Busy
            "quota" in normalized || "battery" in normalized -> PromptFailureKind.Quota
            else -> PromptFailureKind.Other
        }
    }

    private fun mapFailureMessage(error: Throwable): String {
        val normalized = error.message.orEmpty().lowercase()
        return when {
            "background_use_blocked" in normalized ->
                "On-device AI can only answer while Vaultist is open on screen."
            "busy" in normalized ->
                "On-device AI is busy right now. Try again in a moment."
            "quota" in normalized || "battery" in normalized ->
                "On-device AI is temporarily unavailable. Try again later."
            else -> "On-device AI could not answer right now."
        }
    }

    companion object {
        private const val TAG = "AicorePromptClient"
        const val DEFAULT_TOKEN_LIMIT = 4096
        private const val STATUS_TIMEOUT_MS = 12_000L
        private const val READY_POLL_ATTEMPTS = 12
        private const val READY_POLL_DELAY_MS = 500L
    }
}
