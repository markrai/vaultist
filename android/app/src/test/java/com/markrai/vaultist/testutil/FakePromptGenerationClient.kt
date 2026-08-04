package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.genai.LocalAiCapability
import com.markrai.vaultist.data.genai.PromptFailureKind
import com.markrai.vaultist.data.genai.PromptGenerationClient
import com.markrai.vaultist.data.genai.PromptGenerationResult
import com.markrai.vaultist.data.genai.PromptRequest

class FakePromptGenerationClient : PromptGenerationClient {
    var capability: LocalAiCapability = LocalAiCapability.Ready(4096)
    var generationResult: PromptGenerationResult = PromptGenerationResult.Success("Answer [1].")
    var generateHandler: (suspend (PromptRequest) -> PromptGenerationResult)? = null
    var tokenLimit: Int = 4096
    var countTokensResult: Int? = null
    var generateCalls: Int = 0
    var lastRequest: PromptRequest? = null
    var closed = false

    override suspend fun checkCapability(): LocalAiCapability = capability

    override suspend fun downloadModel(): LocalAiCapability {
        capability = LocalAiCapability.Ready(tokenLimit)
        return capability
    }

    override suspend fun getTokenLimit(): Int? = tokenLimit

    override suspend fun countTokens(request: PromptRequest): Int? {
        lastRequest = request
        return countTokensResult ?: (request.userText.length / 4 + request.systemInstruction.length / 4)
    }

    override suspend fun generate(request: PromptRequest): PromptGenerationResult {
        generateCalls += 1
        lastRequest = request
        return generateHandler?.invoke(request) ?: generationResult
    }

    override fun close() {
        closed = true
    }

    fun failGeneration(kind: PromptFailureKind = PromptFailureKind.Other, message: String = "failed") {
        generationResult = PromptGenerationResult.Failure(kind, message)
    }
}
