package com.markrai.vaultist.data.ask

object PassageExtractor {
    private val frontmatter = Regex("""^---\s*\n.*?\n---\s*\n""", RegexOption.DOT_MATCHES_ALL)
    private val dataUrl = Regex("""data:[^)\s]+""", RegexOption.IGNORE_CASE)
    private val heading = Regex("""^#{1,6}\s+(.+)$""", RegexOption.MULTILINE)
    private val codeFence = Regex("""```[\s\S]*?```""")

    fun extractPassages(
        noteId: String,
        title: String,
        path: String,
        body: String,
        analyzed: AnalyzedQuery,
        maxPassages: Int = 2,
    ): List<AskPassage> {
        val subjectTerms = analyzed.subjectTerms.ifEmpty { analyzed.searchTerms }
        val rankingTerms = (subjectTerms + analyzed.intentTerms).distinct()
        val sanitized = sanitize(body, rankingTerms)
        if (sanitized.isBlank()) return emptyList()

        val windows = buildList {
            bestCoverageWindow(sanitized, subjectTerms)?.let { add(ScoredPassage(it, scoreWindow(it, analyzed))) }
            headingBlock(sanitized, rankingTerms)?.let { add(ScoredPassage(it, scoreWindow(it, analyzed) + 5)) }
            firstSubstantiveParagraph(sanitized)?.let { add(ScoredPassage(it, scoreWindow(it, analyzed) - 5)) }
            val head = if (sanitized.length <= 600) sanitized else sanitized.take(600).trim()
            add(ScoredPassage(head, scoreWindow(head, analyzed) - 10))
        }

        return windows
            .filter { it.text.isNotBlank() }
            .sortedByDescending { it.score }
            .distinctBy { it.text.take(120) }
            .take(maxPassages)
            .map { scored ->
                AskPassage(
                    noteId = noteId,
                    title = title,
                    path = path,
                    text = scored.text.trim(),
                )
            }
    }

    fun scoreWindow(text: String, analyzed: AnalyzedQuery): Int {
        val lower = text.lowercase()
        val subjectHits = analyzed.subjectTerms.count { term ->
            lower.contains(term) || lower.contains(AskQueryAnalyzer.lightStem(term))
        }
        val intentHits = analyzed.intentTerms.count { term ->
            lower.contains(term) || lower.contains(AskQueryAnalyzer.lightStem(term))
        }
        var score = subjectHits * 20 + intentHits * 4

        // Proximity: reward when two subject terms appear within a short span.
        if (analyzed.subjectTerms.size >= 2) {
            val positions = analyzed.subjectTerms.mapNotNull { term ->
                val idx = lower.indexOf(term).takeIf { it >= 0 }
                    ?: lower.indexOf(AskQueryAnalyzer.lightStem(term)).takeIf { it >= 0 }
                idx
            }.sorted()
            if (positions.size >= 2) {
                val span = positions.last() - positions.first()
                if (span <= 120) score += 25
                else if (span <= 280) score += 10
            }
        }

        for (phrase in analyzed.quotedPhrases) {
            if (lower.contains(phrase)) score += 30
        }
        return score
    }

    private data class ScoredPassage(val text: String, val score: Int)

    private fun sanitize(body: String, terms: List<String>): String {
        var text = body.replace(frontmatter, "")
        text = text.replace(dataUrl, "[attachment omitted]")
        text = text.replace(Regex("""!\[[^\]]*]\([^)]+\)"""), "")
        text = text.replace(Regex("""\[\[[^\]]+]]"""), "")
        text = preserveRelevantCode(text, terms)
        return text.lines()
            .map { it.trimEnd() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun preserveRelevantCode(body: String, terms: List<String>): String {
        val technical = terms.any { it.length >= 3 }
        return codeFence.replace(body) { match ->
            val block = match.value
            val relevant = technical && terms.any { term ->
                term.length >= 3 && block.contains(term, ignoreCase = true)
            }
            if (relevant) {
                if (block.length <= 900) block else block.take(900) + "\n```"
            } else {
                "```\n[code omitted]\n```"
            }
        }
    }

    private fun bestCoverageWindow(text: String, terms: List<String>): String? {
        if (terms.isEmpty()) return null
        val lower = text.lowercase()
        val positions = terms.mapNotNull { term ->
            val stem = AskQueryAnalyzer.lightStem(term)
            sequenceOf(term, stem).map { lower.indexOf(it) }.firstOrNull { it >= 0 }
        }.sorted()
        if (positions.isEmpty()) return null

        // Prefer a window covering as many term hits as possible.
        val center = if (positions.size == 1) {
            positions.first()
        } else {
            positions[positions.size / 2]
        }
        val start = (center - 200).coerceAtLeast(0)
        val end = (center + 450).coerceAtMost(text.length)
        return text.substring(start, end).trim()
    }

    private fun headingBlock(text: String, terms: List<String>): String? {
        val matches = heading.findAll(text).toList()
        if (matches.isEmpty()) return null
        val target = matches.firstOrNull { match ->
            terms.any { term -> match.groupValues[1].contains(term, ignoreCase = true) }
        } ?: return null
        val start = target.range.first
        val end = text.indexOf("\n\n", start + 1).let { if (it < 0) text.length else it }
        return text.substring(start, end.coerceAtMost(start + 500)).trim()
    }

    private fun firstSubstantiveParagraph(text: String): String? {
        return text.split("\n\n")
            .map { it.trim() }
            .firstOrNull { paragraph ->
                paragraph.length >= 80 && !paragraph.startsWith("#")
            }
    }
}
