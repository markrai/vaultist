package com.markrai.vaultist.ui.note

private val TASK_UNCHECKED = Regex("""\[ \]""")
private val TASK_CHECKED = Regex("""\[[xX]\]""")

/** Flips the first GFM task marker on [sourceLine] (1-based). Returns null when no change applies. */
fun toggleTaskLine(content: String, sourceLine: Int): String? {
    val lines = content.replace("\r\n", "\n").split('\n').toMutableList()
    val index = sourceLine - 1
    if (index !in lines.indices) return null
    val line = lines[index]
    val updated = when {
        TASK_UNCHECKED.containsMatchIn(line) -> TASK_UNCHECKED.replaceFirst(line, "[x]")
        TASK_CHECKED.containsMatchIn(line) -> TASK_CHECKED.replaceFirst(line, "[ ]")
        else -> return null
    }
    if (updated == line) return null
    lines[index] = updated
    return lines.joinToString("\n")
}
