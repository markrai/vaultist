package com.markrai.vaultist.ui.markdown

sealed interface MarkdownBlock {
    val sourceLine: Int
    data class Heading(val level: Int, val text: String, override val sourceLine: Int) : MarkdownBlock
    data class Paragraph(val text: String, override val sourceLine: Int) : MarkdownBlock
    data class ListItem(val ordered: Boolean, val number: Int?, val text: String, override val sourceLine: Int) : MarkdownBlock
    data class Quote(val text: String, override val sourceLine: Int) : MarkdownBlock
    data class Code(val language: String?, val content: String, override val sourceLine: Int) : MarkdownBlock
}

object MarkdownDocumentParser {
    fun parse(markdown: String): List<MarkdownBlock> {
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val result = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        var index = if (lines.firstOrNull()?.trim() == "---") skipFrontmatter(lines) else 0
        var paragraphStart = index + 1

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                result += MarkdownBlock.Paragraph(paragraph.joinToString("\n"), paragraphStart)
                paragraph.clear()
            }
        }

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()
            val fence = fenceMarker(trimmed)
            if (fence != null) {
                flushParagraph()
                val sourceLine = index + 1
                val language = trimmed.drop(fence.length).trim().takeIf(String::isNotEmpty)
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith(fence)) code += lines[index++]
                if (index < lines.size) index++
                result += MarkdownBlock.Code(language, code.joinToString("\n"), sourceLine)
                continue
            }
            val headingLevel = trimmed.takeWhile { it == '#' }.length
            when {
                line.isBlank() -> flushParagraph()
                headingLevel in 1..6 && trimmed.getOrNull(headingLevel) == ' ' -> {
                    flushParagraph(); result += MarkdownBlock.Heading(headingLevel, trimmed.drop(headingLevel + 1).trim(), index + 1)
                }
                trimmed.startsWith("> ") -> {
                    flushParagraph(); result += MarkdownBlock.Quote(trimmed.drop(2), index + 1)
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    flushParagraph(); result += MarkdownBlock.ListItem(false, null, trimmed.drop(2), index + 1)
                }
                orderedPrefixLength(trimmed) > 0 -> {
                    flushParagraph()
                    val prefix = orderedPrefixLength(trimmed)
                    result += MarkdownBlock.ListItem(true, trimmed.take(prefix - 2).toIntOrNull(), trimmed.drop(prefix), index + 1)
                }
                else -> {
                    if (paragraph.isEmpty()) paragraphStart = index + 1
                    paragraph += line
                }
            }
            index++
        }
        flushParagraph()
        return result
    }

    private fun skipFrontmatter(lines: List<String>): Int {
        for (index in 1 until lines.size) if (lines[index].trim() == "---" || lines[index].trim() == "...") return index + 1
        return 0
    }

    private fun fenceMarker(line: String): String? {
        val character = line.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
        val count = line.takeWhile { it == character }.length
        return if (count >= 3) character.toString().repeat(count) else null
    }

    private fun orderedPrefixLength(line: String): Int {
        val digits = line.takeWhile(Char::isDigit)
        return if (digits.isNotEmpty() && line.drop(digits.length).startsWith(". ")) digits.length + 2 else 0
    }
}

fun headingSlug(value: String): String = value.lowercase().trim()
    .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '-' }
    .replace(Regex("\\s+"), "-")
    .trim('-')
