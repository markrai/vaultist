package com.markrai.vaultist.data.ask

import com.markrai.vaultist.data.genai.PromptFailureKind
import com.markrai.vaultist.data.genai.PromptGenerationClient
import com.markrai.vaultist.data.genai.PromptGenerationResult
import com.markrai.vaultist.data.genai.PromptRequest
import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.data.settings.AskPreferences
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.VaultResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class VaultAskEngine @Inject constructor(
    private val repository: VaultRepository,
    private val promptClient: PromptGenerationClient,
    private val askPreferences: AskPreferences,
) {
    suspend fun ask(
        question: String,
        requestId: Long,
        isActive: () -> Boolean,
        onStage: (AskStage) -> Unit = {},
    ): AskOutcome {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return AskOutcome.Failure("Enter a question to ask your vault.")

        val analyzed = AskQueryAnalyzer.analyze(trimmed)
        if (!analyzed.hasUsableTerms) {
            return AskOutcome.NoMatches(
                "I could not find searchable subject terms in that question. Try naming a topic, project, or note.",
            )
        }

        if (!isActive()) return AskOutcome.Cancelled

        onStage(AskStage.SearchingHost)
        val searchHits = searchTerms(analyzed, isActive)
        if (!isActive()) return AskOutcome.Cancelled

        if (searchHits == null) {
            return AskOutcome.Failure(
                message = "Could not search the vault. Check Tailscale and the server URL.",
                retryable = true,
            )
        }

        val fused = RetrievalFusion.fuse(searchHits, analyzed)
        val candidates = RetrievalFusion.filterRelevant(fused, analyzed).take(TOP_CANDIDATES)

        if (candidates.isEmpty()) {
            return AskOutcome.NoMatches(
                if (fused.isEmpty()) {
                    "No matching notes found."
                } else {
                    "I found some notes, but none looked relevant enough to answer from."
                },
            )
        }

        if (!isActive()) return AskOutcome.Cancelled

        onStage(AskStage.LoadingNotes)
        val fetchedNotes = fetchNotes(candidates, isActive)
        if (!isActive()) return AskOutcome.Cancelled

        if (fetchedNotes.isEmpty()) {
            return AskOutcome.Failure("Could not load note content for answering.", retryable = true)
        }

        val passages = selectPassages(analyzed, fetchedNotes)
        if (passages.isEmpty()) {
            return AskOutcome.Partial(
                message = "I found potentially relevant notes, but could not extract usable evidence.",
                sources = fetchedNotes.map { it.toBrowseItem() },
            )
        }

        val tokenLimit = promptClient.getTokenLimit() ?: DEFAULT_TOKEN_LIMIT
        val packed = packPassages(trimmed, passages, tokenLimit)
        if (packed.isEmpty()) {
            return AskOutcome.Partial(
                message = "I found potentially relevant notes, but the evidence was too large to pack.",
                sources = fetchedNotes.map { it.toBrowseItem() },
            )
        }

        if (!isActive()) return AskOutcome.Cancelled

        onStage(AskStage.AnsweringOnDevice)

        val enableThinking = askPreferences.enableAskThinking.first()
        val userText = AskPromptComposer.buildUserText(trimmed, packed)
        val request = PromptRequest(
            systemInstruction = AskPromptComposer.SYSTEM_INSTRUCTION,
            userText = userText,
            enableThinking = enableThinking,
        )

        if (!isActive()) return AskOutcome.Cancelled

        val generation = withTimeoutOrNull(ASK_TIMEOUT_MS) {
            promptClient.generate(request)
        }

        if (!isActive()) return AskOutcome.Cancelled

        val sources = packed.map { passage ->
            BrowseItem(
                kind = BrowseKind.Note,
                id = passage.noteId,
                name = passage.title,
                title = passage.title,
                path = passage.path,
                error = null,
            )
        }

        return when (generation) {
            null -> AskOutcome.Partial(
                message = "Ask timed out. I found these potentially relevant notes, but could not generate an answer.",
                sources = sources,
            )
            is PromptGenerationResult.Success -> {
                val validated = CitationValidator.validate(generation.text, packed.size)
                AskOutcome.Success(
                    answer = validated.displayText.trim(),
                    sources = sources,
                    hadInvalidCitations = validated.hadInvalidCitations,
                )
            }
            is PromptGenerationResult.Failure -> when (generation.kind) {
                PromptFailureKind.Cancelled -> AskOutcome.Cancelled
                else -> AskOutcome.Partial(
                    message = "I found these potentially relevant notes, but could not generate an answer.",
                    sources = sources,
                )
            }
        }
    }

    /**
     * Runs Files+Content search for each retrieval term with bounded concurrency.
     * Returns null only when every request failed (network/auth).
     */
    private suspend fun searchTerms(
        analyzed: AnalyzedQuery,
        isActive: () -> Boolean,
    ): List<TermSearchHit>? = coroutineScope {
        val semaphore = Semaphore(MAX_SEARCH_CONCURRENCY)

        val jobs = analyzed.searchTerms.flatMap { term ->
            listOf(SearchMode.Files to HitMode.Files, SearchMode.Content to HitMode.Content).map { (mode, hitMode) ->
                async {
                    if (!isActive()) return@async SearchAttempt.Cancelled
                    semaphore.withPermit {
                        if (!isActive()) return@withPermit SearchAttempt.Cancelled
                        when (val result = repository.searchNotes(term, mode)) {
                            is VaultResult.Success -> SearchAttempt.Ok(
                                result.value.items.mapIndexedNotNull { index, item ->
                                    if (item.id == null) null
                                    else TermSearchHit(term = term, mode = hitMode, rank = index, item = item)
                                },
                            )
                            is VaultResult.Failure -> SearchAttempt.Failed
                        }
                    }
                }
            }
        }

        val attempts = jobs.awaitAll()
        val hits = attempts.filterIsInstance<SearchAttempt.Ok>().flatMap { it.hits }
        val anySuccess = attempts.any { it is SearchAttempt.Ok }
        val anyFailure = attempts.any { it is SearchAttempt.Failed }
        when {
            hits.isNotEmpty() || anySuccess -> hits
            anyFailure -> null
            else -> emptyList()
        }
    }

    private sealed interface SearchAttempt {
        data class Ok(val hits: List<TermSearchHit>) : SearchAttempt
        data object Failed : SearchAttempt
        data object Cancelled : SearchAttempt
    }

    private suspend fun fetchNotes(
        candidates: List<RetrievalCandidate>,
        isActive: () -> Boolean,
    ): List<FetchedNote> = coroutineScope {
        val semaphore = Semaphore(MAX_NOTE_FETCH_CONCURRENCY)
        candidates.map { candidate ->
            async {
                if (!isActive()) return@async null
                semaphore.withPermit {
                    if (!isActive()) return@withPermit null
                    when (val result = repository.getNote(candidate.noteId)) {
                        is VaultResult.Success -> {
                            val note = result.value
                            if (note.content.isBlank()) null
                            else FetchedNote(
                                noteId = note.id,
                                title = note.title.ifBlank { candidate.title },
                                path = note.path.ifBlank { candidate.path },
                                content = note.content,
                            )
                        }
                        is VaultResult.Failure -> null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun selectPassages(analyzed: AnalyzedQuery, notes: List<FetchedNote>): List<AskPassage> {
        val perNote = notes.flatMap { note ->
            PassageExtractor.extractPassages(
                noteId = note.noteId,
                title = note.title,
                path = note.path,
                body = note.content,
                analyzed = analyzed,
                maxPassages = 2,
            )
        }

        val firstPass = linkedSetOf<String>()
        val selected = mutableListOf<AskPassage>()
        perNote.forEach { passage ->
            if (firstPass.add(passage.noteId)) {
                selected += passage
            }
        }
        perNote.forEach { passage ->
            if (passage !in selected) selected += passage
        }
        return selected
    }

    private suspend fun packPassages(
        question: String,
        passages: List<AskPassage>,
        tokenLimit: Int,
    ): List<AskPassage> {
        val inputBudget = minOf(
            INPUT_BUDGET_CAP,
            tokenLimit - MAX_OUTPUT_TOKENS,
        ) - SAFETY_MARGIN
        if (inputBudget <= 0) return emptyList()

        var packed = mutableListOf<AskPassage>()
        var charBudget = inputBudget * CHARS_PER_TOKEN_ESTIMATE

        for (passage in passages) {
            val trimmed = passage.copy(text = passage.text.take(charBudget.coerceAtLeast(0)))
            if (trimmed.text.isBlank()) break
            packed += trimmed
            charBudget -= trimmed.text.length
            if (charBudget <= 0) break
        }

        packed = trimToTokenBudget(question, packed, inputBudget).toMutableList()
        return packed.filter { it.text.isNotBlank() }
    }

    private suspend fun trimToTokenBudget(
        question: String,
        passages: List<AskPassage>,
        inputBudget: Int,
    ): List<AskPassage> {
        var current = passages.toMutableList()
        repeat(MAX_TOKEN_TRIM_ITERATIONS) {
            val userText = AskPromptComposer.buildUserText(question, current)
            val tokens = promptClient.countTokens(
                PromptRequest(
                    systemInstruction = AskPromptComposer.SYSTEM_INSTRUCTION,
                    userText = userText,
                ),
            ) ?: return current
            if (tokens <= inputBudget) return current

            val last = current.lastOrNull() ?: return current
            val shortened = last.copy(text = last.text.dropLast((last.text.length * 0.15).toInt().coerceAtLeast(80)))
            if (shortened.text.length < 40) {
                current.removeAt(current.lastIndex)
            } else {
                current[current.lastIndex] = shortened
            }
        }
        return current
    }

    private data class FetchedNote(
        val noteId: String,
        val title: String,
        val path: String,
        val content: String,
    ) {
        fun toBrowseItem() = BrowseItem(
            kind = BrowseKind.Note,
            id = noteId,
            name = title,
            title = title,
            path = path,
            error = null,
        )
    }

    companion object {
        private const val TOP_CANDIDATES = 8
        private const val MAX_NOTE_FETCH_CONCURRENCY = 3
        private const val MAX_SEARCH_CONCURRENCY = 4
        private const val SAFETY_MARGIN = 96
        private const val MAX_OUTPUT_TOKENS = 256
        private const val INPUT_BUDGET_CAP = 4000
        private const val DEFAULT_TOKEN_LIMIT = 4096
        private const val CHARS_PER_TOKEN_ESTIMATE = 4
        private const val MAX_TOKEN_TRIM_ITERATIONS = 6
        private const val ASK_TIMEOUT_MS = 45_000L
    }
}
