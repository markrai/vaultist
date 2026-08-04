package com.markrai.vaultist.data.ask

data class CitationValidationResult(
    val displayText: String,
    val hadInvalidCitations: Boolean,
)

object CitationValidator {
    private val strictCitation = Regex("""\[\d+\]""")

    fun validate(answer: String, validCitationCount: Int): CitationValidationResult {
        if (validCitationCount <= 0) {
            return CitationValidationResult(answer, hadInvalidCitations = false)
        }

        var hadInvalid = false
        val deduped = dedupeAdjacentCitations(answer)
        val cleaned = strictCitation.replace(deduped) { match ->
            val number = match.value.removePrefix("[").removeSuffix("]").toIntOrNull()
            when {
                number != null && number in 1..validCitationCount -> match.value
                number != null && match.value.length <= 4 -> {
                    hadInvalid = true
                    ""
                }
                else -> match.value
            }
        }
        return CitationValidationResult(cleaned, hadInvalid)
    }

    private fun dedupeAdjacentCitations(text: String): String {
        var current = text
        while (true) {
            val next = current.replace(Regex("""(\[\d+\])\1+"""), "$1")
            if (next == current) return current
            current = next
        }
    }
}
