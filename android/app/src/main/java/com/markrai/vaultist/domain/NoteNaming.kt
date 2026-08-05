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

/** Maps an unresolved wiki/markdown link target to a note id for create-from-missing-link. */
fun noteIdFromMissingLink(sourceNoteId: String, target: String): NoteTitleValidation {
    val trimmed = target.trim().trim('/')
    if (trimmed.isEmpty()) return NoteTitleValidation(null, null, "Enter a valid title.")
    val withoutMd = trimmed.removeSuffix(".md").removeSuffix(".MD")
    return if (withoutMd.contains('/')) {
        noteIdFromTitle(
            folder = withoutMd.substringBeforeLast('/'),
            title = withoutMd.substringAfterLast('/'),
        )
    } else {
        noteIdFromTitle(parentFolderOfNoteId(sourceNoteId), withoutMd)
    }
}

fun parentFolderOfNoteId(noteId: String): String {
    val slash = noteId.lastIndexOf('/')
    return if (slash < 0) "" else noteId.substring(0, slash)
}

fun sanitizeNoteLeaf(title: String): String? {
    var leaf = title.trim().replace(invalidLeafChars, "-")
    leaf = leaf.replace(whitespaceRuns, " ").trim()
    leaf = leaf.replace(dashRuns, "-").trim('-', ' ')
    if (leaf.isEmpty() || leaf == "." || leaf == "..") return null
    return leaf
}
