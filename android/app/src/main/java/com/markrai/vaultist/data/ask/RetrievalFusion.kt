package com.markrai.vaultist.data.ask

import com.markrai.vaultist.domain.BrowseItem

data class TermSearchHit(
    val term: String,
    val mode: HitMode,
    val rank: Int,
    val item: BrowseItem,
)

enum class HitMode { Files, Content }

data class RetrievalCandidate(
    val noteId: String,
    val title: String,
    val path: String,
    val matchedTerms: Set<String>,
    val filesRanks: Map<String, Int>,
    val contentRanks: Map<String, Int>,
    val score: Int,
    val strong: Boolean,
)

/**
 * Multi-term retrieval fusion: score by distinct subject-term coverage, not server order alone.
 */
object RetrievalFusion {
    private const val EXACT_TITLE = 100
    private const val TITLE_CONTAINS = 80
    private const val FILES_HIT = 50
    private const val CONTENT_HIT = 30
    private const val EXTRA_SUBJECT_TERM = 40
    private const val BOTH_CHANNELS = 20
    private const val PATH_CONTAINS = 25

    fun fuse(
        hits: List<TermSearchHit>,
        analyzed: AnalyzedQuery,
    ): List<RetrievalCandidate> {
        if (hits.isEmpty()) return emptyList()

        val subjectStems = analyzed.subjectTerms.map { AskQueryAnalyzer.lightStem(it) }.toSet()
        val byId = linkedMapOf<String, MutableCandidate>()

        for (hit in hits) {
            val id = hit.item.id ?: continue
            val entry = byId.getOrPut(id) { MutableCandidate(id, hit.item) }
            entry.matchedTerms += hit.term
            entry.matchedStems += AskQueryAnalyzer.lightStem(hit.term)
            when (hit.mode) {
                HitMode.Files -> {
                    entry.filesRanks[hit.term] = minOf(entry.filesRanks[hit.term] ?: Int.MAX_VALUE, hit.rank)
                }
                HitMode.Content -> {
                    entry.contentRanks[hit.term] = minOf(entry.contentRanks[hit.term] ?: Int.MAX_VALUE, hit.rank)
                }
            }
            entry.title = hit.item.title?.ifBlank { null } ?: hit.item.name.ifBlank { entry.title }
            entry.path = hit.item.path.ifBlank { entry.path }
        }

        return byId.values
            .map { it.toCandidate(subjectStems, analyzed) }
            .sortedWith(
                compareByDescending<RetrievalCandidate> { it.score }
                    .thenByDescending { it.matchedTerms.size }
                    .thenBy { it.filesRanks.values.minOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.contentRanks.values.minOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.noteId },
            )
    }

    fun filterRelevant(
        candidates: List<RetrievalCandidate>,
        analyzed: AnalyzedQuery,
    ): List<RetrievalCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val subjectCount = analyzed.subjectTerms.size
        return candidates.filter { candidate ->
            val subjectHits = candidate.matchedSubjectStemCount(analyzed)
            when {
                candidate.strong && subjectHits >= 1 -> true
                subjectCount >= 2 -> subjectHits >= 2
                subjectCount == 1 -> subjectHits >= 1 && candidate.score >= 50
                else -> candidate.score >= 80
            }
        }
    }

    private fun RetrievalCandidate.matchedSubjectStemCount(analyzed: AnalyzedQuery): Int {
        val subjectStems = analyzed.subjectTerms.map { AskQueryAnalyzer.lightStem(it) }.toSet()
        if (subjectStems.isEmpty()) return matchedTerms.size
        return matchedTerms.count { AskQueryAnalyzer.lightStem(it) in subjectStems }
    }

    private class MutableCandidate(noteId: String, hit: BrowseItem) {
        var noteId: String = noteId
        var title: String = hit.title?.ifBlank { null } ?: hit.name
        var path: String = hit.path
        val matchedTerms = linkedSetOf<String>()
        val matchedStems = linkedSetOf<String>()
        val filesRanks = linkedMapOf<String, Int>()
        val contentRanks = linkedMapOf<String, Int>()

        fun toCandidate(subjectStems: Set<String>, analyzed: AnalyzedQuery): RetrievalCandidate {
            val titleLower = title.lowercase()
            val pathLower = path.lowercase()
            val subjectMatched = matchedStems.count { it in subjectStems }.coerceAtLeast(
                if (subjectStems.isEmpty()) matchedStems.size else 0,
            )

            var score = 0
            for (term in matchedTerms) {
                val stem = AskQueryAnalyzer.lightStem(term)
                if (titleLower == term || titleLower == stem) score += EXACT_TITLE
                else if (titleLower.contains(term) || titleLower.contains(stem)) score += TITLE_CONTAINS
                if (pathLower.contains(term) || pathLower.contains(stem)) score += PATH_CONTAINS
            }
            if (filesRanks.isNotEmpty()) score += FILES_HIT
            if (contentRanks.isNotEmpty()) score += CONTENT_HIT
            if (filesRanks.isNotEmpty() && contentRanks.isNotEmpty()) score += BOTH_CHANNELS
            if (subjectMatched > 1) score += EXTRA_SUBJECT_TERM * (subjectMatched - 1)

            // Small rank-position bonus (better = lower rank index).
            val bestRank = (filesRanks.values + contentRanks.values).minOrNull() ?: 50
            score += (10 - bestRank.coerceAtMost(10)).coerceAtLeast(0)

            val strong = subjectMatched >= 2 ||
                (subjectMatched == 1 && analyzed.subjectTerms.size <= 1 && score >= 50)

            return RetrievalCandidate(
                noteId = noteId,
                title = title,
                path = path,
                matchedTerms = matchedTerms.toSet(),
                filesRanks = filesRanks.toMap(),
                contentRanks = contentRanks.toMap(),
                score = score,
                strong = strong,
            )
        }
    }
}
