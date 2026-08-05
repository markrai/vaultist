package com.markrai.vaultist.domain

data class NoteTitleValidation(
    val id: String?,
    val leaf: String?,
    val error: String?,
)

private val invalidLeafChars = Regex("""[\\/:*?"<>|\x00-\x1f]""")
private val whitespaceRuns = Regex("""\s+""")
private val dashRuns = Regex("""-+""")

fun noteIdFromTitle(folder: String, title: String): NoteTitleValidation {
    val leaf = sanitizeNoteLeaf(title)
        ?: return NoteTitleValidation(null, null, "Enter a valid title.")
    val normalizedFolder = folder.trim().trim('/')
    val id = if (normalizedFolder.isEmpty()) leaf else "$normalizedFolder/$leaf"
    return NoteTitleValidation(id, leaf, null)
}

fun sanitizeNoteLeaf(title: String): String? {
    var leaf = title.trim().replace(invalidLeafChars, "-")
    leaf = leaf.replace(whitespaceRuns, " ").trim()
    leaf = leaf.replace(dashRuns, "-").trim('-', ' ')
    if (leaf.isEmpty() || leaf == "." || leaf == "..") return null
    return leaf
}
