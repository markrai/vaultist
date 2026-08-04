package com.markrai.vaultist.data.genai

interface PromptGenerationClient {
    suspend fun checkCapability(): LocalAiCapability
    suspend fun downloadModel(): LocalAiCapability
    suspend fun getTokenLimit(): Int?
    suspend fun countTokens(request: PromptRequest): Int?
    suspend fun generate(request: PromptRequest): PromptGenerationResult
    fun close()
}

data class PromptRequest(
    val systemInstruction: String,
    val userText: String,
    val maxOutputTokens: Int = 256,
    val temperature: Float = 0.2f,
    val enableThinking: Boolean = false,
)
