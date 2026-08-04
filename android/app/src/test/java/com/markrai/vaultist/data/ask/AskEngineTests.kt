package com.markrai.vaultist.data.ask

import com.markrai.vaultist.data.genai.PromptGenerationResult
import com.markrai.vaultist.di.config.AskRuntimeConfig
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.testutil.FakeAskPreferences
import com.markrai.vaultist.testutil.FakePromptGenerationClient
import com.markrai.vaultist.testutil.FakeVaultRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskQueryAnalyzerTest {
    @Test
    fun extractsSubjectTermsAndDownranksIntent() {
        val analyzed = AskQueryAnalyzer.analyze("What did I decide about the deployment plan?")
        assertTrue(analyzed.subjectTerms.any { it.startsWith("deploy") })
        assertTrue("plan" in analyzed.subjectTerms || analyzed.subjectTerms.any { it.contains("plan") })
        assertTrue(analyzed.intentTerms.any { AskQueryAnalyzer.lightStem(it) == "decide" })
        assertFalse("decide" in analyzed.searchTerms || "decided" in analyzed.searchTerms)
        assertFalse(analyzed.searchTerms.any { it == "what" || it == "about" || it == "the" })
    }

    @Test
    fun prefersSubjectsOverIntentForSearch() {
        val analyzed = AskQueryAnalyzer.analyze("How did I expose Paperless through Tailscale?")
        assertTrue(analyzed.subjectTerms.contains("paperless"))
        assertTrue(analyzed.subjectTerms.contains("tailscale") || analyzed.subjectTerms.contains("expose"))
        assertTrue(analyzed.searchTerms.contains("paperless"))
    }

    @Test
    fun allStopwordsYieldsNoUsableTerms() {
        val analyzed = AskQueryAnalyzer.analyze("What did I do?")
        assertFalse(analyzed.hasUsableTerms)
    }

    @Test
    fun stemsDeploymentToDeploy() {
        assertEquals("deploy", AskQueryAnalyzer.lightStem("deployment"))
        assertEquals("deploy", AskQueryAnalyzer.lightStem("deploying"))
    }
}

class RetrievalCoverageTest {
    @Test
    fun multiTermCoverageOutranksSingleGenericHit() {
        val relevant = note("Notes/Deploy", "Vaultist on Vega")
        val weak = note("Notes/Plans", "Plan")
        val hits = listOf(
            TermSearchHit("Vaultist", HitMode.Content, 0, relevant),
            TermSearchHit("vega", HitMode.Content, 0, relevant),
            TermSearchHit("plan", HitMode.Files, 0, relevant),
            TermSearchHit("plan", HitMode.Content, 0, weak),
        )
        val analyzed = AskQueryAnalyzer.analyze("What was the plan for Vaultist on Vega?")
        val fused = RetrievalFusion.fuse(hits, analyzed)
        assertEquals("Notes/Deploy", fused.first().noteId)
        val relevantOnly = RetrievalFusion.filterRelevant(fused, analyzed)
        assertTrue(relevantOnly.any { it.noteId == "Notes/Deploy" })
        assertFalse(relevantOnly.any { it.noteId == "Notes/Plans" })
    }

    @Test
    fun singleRareSubjectTermIsAcceptable() {
        val hit = note("Notes/Lectric", "Lectric XP Lite 2")
        val hits = listOf(TermSearchHit("lectric", HitMode.Content, 0, hit))
        val analyzed = AnalyzedQuery(
            originalQuestion = "What did I buy for towing?",
            subjectTerms = listOf("lectric"),
            intentTerms = listOf("buy"),
            searchTerms = listOf("lectric"),
        )
        val filtered = RetrievalFusion.filterRelevant(RetrievalFusion.fuse(hits, analyzed), analyzed)
        assertEquals(1, filtered.size)
    }

    private fun note(id: String, title: String) =
        BrowseItem(BrowseKind.Note, id, "$title.md", title, "$id.md", null)
}

class CitationValidatorTest {
    @Test
    fun keepsValidCitationsAndRemovesInvalidOnes() {
        val result = CitationValidator.validate("See [1] and [9] and version [2024].", validCitationCount = 2)
        assertEquals("See [1] and  and version [2024].", result.displayText)
        assertTrue(result.hadInvalidCitations)
    }

    @Test
    fun dedupesAdjacentDuplicateMarkers() {
        val result = CitationValidator.validate("Same source [1][1] here.", validCitationCount = 1)
        assertEquals("Same source [1] here.", result.displayText)
    }
}

class VaultAskEngineRetrievalTest {
    @Test
    fun naturalLanguageQuestionRetrievesBySubjectTermsNotFullPhrase() = runTest {
        val deployNote = Note(
            id = "Notes/Deploy",
            path = "Notes/Deploy.md",
            filename = "Deploy.md",
            title = "Production release",
            aliases = emptyList(),
            headings = emptyList(),
            links = emptyList(),
            attachments = emptyList(),
            modifiedAt = "2026-01-01T00:00:00Z",
            size = 0L,
            revision = "1",
            content = "The production release will use Docker Compose on Vega.",
            error = null,
        )
        val item = BrowseItem(BrowseKind.Note, deployNote.id, deployNote.filename, deployNote.title, deployNote.path, null)
        val repository = FakeVaultRepository().apply {
            termIndex = mapOf(
                "docker" to listOf(item),
                "compose" to listOf(item),
                "vega" to listOf(item),
                "deployment" to listOf(item),
                "deploy" to listOf(item),
            )
            notesById = mapOf(deployNote.id to deployNote)
        }
        val prompt = FakePromptGenerationClient().apply {
            generationResult = PromptGenerationResult.Success("Use Docker Compose on Vega. [1]")
        }
        val engine = VaultAskEngine(repository, prompt, FakeAskPreferences(), AskRuntimeConfig())

        val outcome = engine.ask(
            question = "What did I decide about deployment?",
            requestId = 1L,
            isActive = { true },
        )

        assertTrue(outcome is AskOutcome.Success)
        assertTrue(repository.searchQueries.none { it.second.contains(" ") })
        assertTrue(repository.searchQueries.any { it.second.contains("deploy", ignoreCase = true) })
        assertTrue(repository.searchQueries.any { it.first == SearchMode.Files })
        assertTrue(repository.searchQueries.any { it.first == SearchMode.Content })
        assertEquals("Notes/Deploy", (outcome as AskOutcome.Success).sources.single().id)
    }

    @Test
    fun rejectsWeakSingleGenericMatchesWhenMultiSubjectQuestion() = runTest {
        val weak = Note(
            id = "Notes/Plans",
            path = "Notes/Plans.md",
            filename = "Plans.md",
            title = "Random plans",
            aliases = emptyList(),
            headings = emptyList(),
            links = emptyList(),
            attachments = emptyList(),
            modifiedAt = "2026-01-01T00:00:00Z",
            size = 0L,
            revision = "1",
            content = "I have many plans for next year.",
            error = null,
        )
        val weakItem = BrowseItem(BrowseKind.Note, weak.id, weak.filename, weak.title, weak.path, null)
        val repository = FakeVaultRepository().apply {
            termIndex = mapOf("plan" to listOf(weakItem), "plans" to listOf(weakItem))
            notesById = mapOf(weak.id to weak)
        }
        val engine = VaultAskEngine(repository, FakePromptGenerationClient(), FakeAskPreferences(), AskRuntimeConfig())

        val outcome = engine.ask(
            question = "What was the plan for Vaultist on Vega?",
            requestId = 1L,
            isActive = { true },
        )

        assertTrue(outcome is AskOutcome.NoMatches)
    }

    @Test
    fun paperlessTailscaleQuestionFindsContentWithoutIntentWords() = runTest {
        val note = Note(
            id = "Notes/Paperless",
            path = "Notes/Paperless.md",
            filename = "Paperless.md",
            title = "Paperless",
            aliases = emptyList(),
            headings = emptyList(),
            links = emptyList(),
            attachments = emptyList(),
            modifiedAt = "2026-01-01T00:00:00Z",
            size = 0L,
            revision = "1",
            content = "sudo tailscale serve --bg https://127.0.0.1:8000",
            error = null,
        )
        val item = BrowseItem(BrowseKind.Note, note.id, note.filename, note.title, note.path, null)
        val repository = FakeVaultRepository().apply {
            termIndex = mapOf(
                "paperless" to listOf(item),
                "tailscale" to listOf(item),
                "expose" to listOf(item),
            )
            notesById = mapOf(note.id to note)
        }
        val prompt = FakePromptGenerationClient().apply {
            generationResult = PromptGenerationResult.Success("Use tailscale serve. [1]")
        }
        val outcome = VaultAskEngine(repository, prompt, FakeAskPreferences(), AskRuntimeConfig()).ask(
            question = "How did I expose Paperless through Tailscale?",
            requestId = 1L,
            isActive = { true },
        )
        assertTrue(outcome is AskOutcome.Success)
        assertEquals("Notes/Paperless", (outcome as AskOutcome.Success).sources.single().id)
    }

    @Test
    fun shortAskTimeoutIsConfigurable() = runTest {
        val note = Note(
            id = "Notes/Test",
            path = "Notes/Test.md",
            filename = "Test.md",
            title = "Test",
            aliases = emptyList(),
            headings = emptyList(),
            links = emptyList(),
            attachments = emptyList(),
            modifiedAt = "2026-01-01T00:00:00Z",
            size = 0L,
            revision = "1",
            content = "Some content about deployment.",
            error = null,
        )
        val item = BrowseItem(BrowseKind.Note, note.id, note.filename, note.title, note.path, null)
        val repository = FakeVaultRepository().apply {
            termIndex = mapOf("deploy" to listOf(item))
            notesById = mapOf(note.id to note)
        }
        val prompt = FakePromptGenerationClient().apply {
            generateHandler = { awaitCancellation() }
        }
        val config = AskRuntimeConfig(askTimeout = java.time.Duration.ofMillis(1))
        val engine = VaultAskEngine(repository, prompt, FakeAskPreferences(), config)

        val outcome = engine.ask(
            question = "What about deployment?",
            requestId = 1L,
            isActive = { true },
        )

        assertTrue(outcome is AskOutcome.Partial)
    }
}
