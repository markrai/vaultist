package com.markrai.vaultist.data.ask

/**
 * Deterministic local query analysis for Ask retrieval.
 * Full natural-language questions stay for Nano; only extracted terms hit Vaultist search.
 */
object AskQueryAnalyzer {
    private val tokenPattern = Regex("""[a-z0-9][a-z0-9_+./-]{1,}""")

    private val stopwords = setOf(
        "a", "an", "the", "and", "or", "but", "if", "then", "else", "when", "where", "who",
        "whom", "whose", "which", "what", "why", "how", "is", "are", "was", "were", "be",
        "been", "being", "am", "do", "does", "did", "doing", "have", "has", "had", "having",
        "can", "could", "should", "would", "will", "shall", "may", "might", "must",
        "to", "of", "in", "on", "at", "for", "from", "by", "with", "about", "into", "over",
        "under", "between", "through", "during", "before", "after", "above", "below",
        "up", "down", "out", "off", "again", "further", "once", "here", "there", "all",
        "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor",
        "not", "only", "own", "same", "so", "than", "too", "very", "just", "also", "as",
        "it", "its", "this", "that", "these", "those", "i", "me", "my", "mine", "we", "our",
        "you", "your", "he", "she", "they", "them", "their", "please",
    )

    /** Question-intent / meta words: useful for passages, weak for note retrieval. */
    private val intentTerms = setOf(
        "decide", "decided", "decision", "decisions",
        "remember", "remembered", "reminder",
        "mention", "mentioned", "mentions",
        "write", "wrote", "written", "noted",
        "think", "thought", "thoughts",
        "tell", "said", "say", "says",
        "find", "found", "looking",
        "explain", "summary", "summarize", "summarise",
        "buy", "bought", "purchase", "purchased",
        "want", "wanted", "need", "needed",
    )

    private const val MAX_SUBJECT_TERMS = 4
    private const val MAX_INTENT_TERMS = 1
    private const val MAX_SEARCH_TERMS = 6

    fun analyze(question: String): AnalyzedQuery {
        val quoted = extractQuotedPhrases(question)
        val tokens = tokenize(question)
            .filterNot { it in stopwords }
            .filter { it.length >= 3 }
            .distinct()

        val subjects = mutableListOf<String>()
        val intents = mutableListOf<String>()
        for (token in tokens) {
            val stem = lightStem(token)
            when {
                token in intentTerms || stem in intentTerms -> {
                    if (intents.none { lightStem(it) == stem }) intents += token
                }
                else -> {
                    if (subjects.none { lightStem(it) == stem }) subjects += token
                }
            }
        }

        // Quoted phrases are strong subject signals.
        for (phrase in quoted) {
            val parts = tokenize(phrase).filterNot { it in stopwords }.filter { it.length >= 3 }
            for (part in parts) {
                if (subjects.none { lightStem(it) == lightStem(part) }) {
                    subjects.add(0, part)
                }
            }
        }

        val subjectTerms = subjects.take(MAX_SUBJECT_TERMS)
        val intentSelected = intents.take(MAX_INTENT_TERMS)

        // Prefer original tokens; add stems only when they differ and room remains.
        val searchTerms = buildList {
            for (term in subjectTerms) {
                if (size >= MAX_SEARCH_TERMS) break
                add(term)
                val stem = lightStem(term)
                if (stem != term && stem.length >= 3 && size < MAX_SEARCH_TERMS) add(stem)
            }
            if (isEmpty()) {
                for (term in intentSelected) {
                    if (size >= MAX_SEARCH_TERMS) break
                    add(term)
                }
            }
        }.distinct()

        return AnalyzedQuery(
            originalQuestion = question.trim(),
            subjectTerms = subjectTerms,
            intentTerms = intentSelected,
            searchTerms = searchTerms,
            quotedPhrases = quoted,
        )
    }

    /** Conservative English suffix chopping for substring search variants. */
    fun lightStem(token: String): String {
        val t = token.lowercase()
        return when {
            t.endsWith("ing") && t.length > 6 -> t.dropLast(3)
            t.endsWith("tion") && t.length > 7 -> t.dropLast(4)
            t.endsWith("ment") && t.length > 7 -> t.dropLast(4) // deployment → deploy
            t.endsWith("ies") && t.length > 5 -> t.dropLast(3) + "y"
            t.endsWith("ed") && t.length > 5 -> t.dropLast(2)
            t.endsWith("es") && t.length > 5 -> t.dropLast(2)
            t.endsWith("s") && !t.endsWith("ss") && t.length > 4 -> t.dropLast(1)
            else -> t
        }
    }

    private fun tokenize(text: String): List<String> =
        tokenPattern.findAll(text.lowercase()).map { it.value }.toList()

    private fun extractQuotedPhrases(text: String): List<String> {
        val matches = Regex("\"([^\"]{2,})\"|'([^']{2,})'").findAll(text)
        return matches.mapNotNull { match ->
            match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.lowercase()?.trim()
        }.toList()
    }
}

data class AnalyzedQuery(
    val originalQuestion: String,
    val subjectTerms: List<String>,
    val intentTerms: List<String>,
    /** Terms actually sent to Vaultist search (subjects + rare fallbacks + variants). */
    val searchTerms: List<String>,
    val quotedPhrases: List<String> = emptyList(),
) {
    val hasUsableTerms: Boolean get() = searchTerms.isNotEmpty()
}
