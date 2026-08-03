package com.vaultview.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.vaultview.domain.LinkCandidate
import com.vaultview.domain.LinkStatus
import com.vaultview.domain.Note
import com.vaultview.domain.NoteLink
import com.vaultview.ui.theme.Spacing

private const val NoteTag = "note"
private const val UrlTag = "url"
private const val MissingTag = "missing"
private const val AmbiguousTag = "ambiguous"

@Composable
fun MarkdownRenderer(
    note: Note,
    fragment: String?,
    assetUrl: (String) -> String?,
    onOpenNote: (String, String?) -> Unit,
    onMissing: (String) -> Unit,
    onAmbiguous: (String, List<LinkCandidate>) -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(note.id, note.revision) { MarkdownDocumentParser.parse(note.content) }
    val listState = rememberLazyListState()
    LaunchedEffect(note.id, fragment, blocks) {
        val target = when {
            fragment.isNullOrBlank() -> -1
            fragment.startsWith("line-") -> {
                val line = fragment.removePrefix("line-").toIntOrNull() ?: 1
                blocks.indexOfFirst { it.sourceLine >= line }.takeIf { it >= 0 } ?: blocks.lastIndex
            }
            else -> blocks.indexOfFirst { it is MarkdownBlock.Heading && headingSlug(it.text) == headingSlug(fragment) }
        }
        if (target >= 0) listState.scrollToItem(target)
    }
    LazyColumn(
        modifier = modifier.padding(horizontal = Spacing.md),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        itemsIndexed(blocks, key = { index, block -> "${block.sourceLine}:$index" }) { _, block ->
            when (block) {
                is MarkdownBlock.Heading -> InlineText(
                    block.text, note.links, headingStyle(block.level), onOpenNote, onMissing, onAmbiguous,
                    modifier = Modifier.padding(top = if (block.level <= 2) Spacing.md else Spacing.sm),
                )
                is MarkdownBlock.Paragraph -> ParagraphBlock(block.text, note, assetUrl, onOpenNote, onMissing, onAmbiguous, onOpenImage)
                is MarkdownBlock.ListItem -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(if (block.ordered) "${block.number ?: 1}." else "•", style = MaterialTheme.typography.body1)
                    InlineText(block.text, note.links, MaterialTheme.typography.body1, onOpenNote, onMissing, onAmbiguous, Modifier.weight(1f))
                }
                is MarkdownBlock.Quote -> Row {
                    Box(Modifier.padding(end = Spacing.sm).background(MaterialTheme.colors.primary).heightIn(min = 24.dp).padding(horizontal = 2.dp))
                    InlineText(
                        block.text, note.links, MaterialTheme.typography.body1.copy(fontStyle = FontStyle.Italic),
                        onOpenNote, onMissing, onAmbiguous, Modifier.weight(1f),
                    )
                }
                is MarkdownBlock.Code -> Card(backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.06f), elevation = 0.dp) {
                    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(Spacing.md)) {
                        block.language?.let { Text(it, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary) }
                        Text(block.content, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.body2)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParagraphBlock(
    text: String,
    note: Note,
    assetUrl: (String) -> String?,
    onOpenNote: (String, String?) -> Unit,
    onMissing: (String) -> Unit,
    onAmbiguous: (String, List<LinkCandidate>) -> Unit,
    onOpenImage: (String) -> Unit,
) {
    val images = remember(text, note.links) { imageLinks(text, note.links) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val displayText = removeImageSyntax(text, note.links)
        if (displayText.isNotBlank()) InlineText(displayText, note.links, MaterialTheme.typography.body1, onOpenNote, onMissing, onAmbiguous)
        images.forEach { link ->
            when (link.resolution.status) {
                LinkStatus.Resolved -> {
                    val assetId = link.resolution.assetId
                    val url = assetId?.let(assetUrl)
                    SubcomposeAsyncImage(
                        model = url,
                        contentDescription = link.display ?: link.target,
                        contentScale = ContentScale.FillWidth,
                        loading = { Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                        error = { Text("Image could not be loaded: ${link.target}", color = MaterialTheme.colors.error) },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).clickable(enabled = assetId != null) { assetId?.let(onOpenImage) },
                    )
                }
                LinkStatus.Ambiguous -> Text(
                    "Ambiguous image: ${link.target} (${link.resolution.candidates.joinToString { it.path }})",
                    color = MaterialTheme.colors.error,
                )
                else -> Text("Missing image: ${link.target}", color = MaterialTheme.colors.error, modifier = Modifier.clickable { onMissing(link.target) })
            }
        }
    }
}

@Composable
private fun InlineText(
    text: String,
    links: List<NoteLink>,
    style: TextStyle,
    onOpenNote: (String, String?) -> Unit,
    onMissing: (String) -> Unit,
    onAmbiguous: (String, List<LinkCandidate>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colors.primary
    val errorColor = MaterialTheme.colors.error
    val annotated = remember(text, links, linkColor, errorColor) { annotatedInline(text, links, linkColor, errorColor) }
    val uriHandler = LocalUriHandler.current
    ClickableText(text = annotated, style = style.copy(color = MaterialTheme.colors.onSurface), modifier = modifier) { offset ->
        annotated.getStringAnnotations(start = offset, end = offset).firstOrNull()?.let { annotation ->
            when (annotation.tag) {
                NoteTag -> {
                    val pieces = annotation.item.split('\n', limit = 2)
                    onOpenNote(pieces[0], pieces.getOrNull(1)?.takeIf(String::isNotBlank))
                }
                UrlTag -> runCatching { uriHandler.openUri(annotation.item) }
                MissingTag -> onMissing(annotation.item)
                AmbiguousTag -> {
                    val match = links.firstOrNull { it.raw == annotation.item && it.resolution.status == LinkStatus.Ambiguous }
                    onAmbiguous(match?.target ?: annotation.item, match?.resolution?.candidates.orEmpty())
                }
            }
        }
    }
}

private fun headingStyle(level: Int): TextStyle = TextStyle(
    fontSize = when (level) { 1 -> 30.sp; 2 -> 24.sp; 3 -> 21.sp; else -> 18.sp },
    fontWeight = FontWeight.Bold,
)

private fun annotatedInline(text: String, links: List<NoteLink>, linkColor: Color, errorColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < text.length) {
        when {
            text.startsWith("`", cursor) -> {
                val end = text.indexOf('`', cursor + 1)
                if (end > cursor) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = linkColor.copy(alpha = 0.10f)))
                    append(text.substring(cursor + 1, end)); pop(); cursor = end + 1
                } else { append(text[cursor++]) }
            }
            text.startsWith("**", cursor) || text.startsWith("__", cursor) -> {
                val marker = text.substring(cursor, cursor + 2); val end = text.indexOf(marker, cursor + 2)
                if (end > cursor) { pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(text.substring(cursor + 2, end)); pop(); cursor = end + 2 }
                else append(text[cursor++])
            }
            (text[cursor] == '*' || text[cursor] == '_') -> {
                val marker = text[cursor]; val end = text.indexOf(marker, cursor + 1)
                if (end > cursor) { pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); append(text.substring(cursor + 1, end)); pop(); cursor = end + 1 }
                else append(text[cursor++])
            }
            text.startsWith("[[", cursor) -> {
                val end = text.indexOf("]]", cursor + 2)
                if (end > cursor) {
                    val raw = text.substring(cursor + 2, end)
                    val targetAndFragment = raw.substringBefore('|')
                    val explicitLabel = raw.substringAfter('|', "")
                    val targetLabel = targetAndFragment.substringBefore('#')
                    val fragmentLabel = targetAndFragment.substringAfter('#', "")
                    val label = explicitLabel.ifBlank { targetLabel.ifBlank { fragmentLabel } }
                    appendAnnotatedLink(label, raw, links, linkColor, errorColor)
                    cursor = end + 2
                } else append(text[cursor++])
            }
            text[cursor] == '[' -> {
                val labelEnd = text.indexOf(']', cursor + 1)
                val targetStart = if (labelEnd >= 0 && text.getOrNull(labelEnd + 1) == '(') labelEnd + 2 else -1
                val targetEnd = if (targetStart >= 0) text.indexOf(')', targetStart) else -1
                if (targetEnd > targetStart) {
                    val label = text.substring(cursor + 1, labelEnd)
                    val target = text.substring(targetStart, targetEnd)
                    val match = links.firstOrNull { it.target == target || it.raw == target }
                    if (match != null) appendResolved(label, match, linkColor, errorColor)
                    else { pushStringAnnotation(UrlTag, target); pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)); append(label); pop(); pop() }
                    cursor = targetEnd + 1
                } else append(text[cursor++])
            }
            else -> append(text[cursor++])
        }
    }
}

private fun AnnotatedString.Builder.appendAnnotatedLink(label: String, raw: String, links: List<NoteLink>, linkColor: Color, errorColor: Color) {
    val match = links.firstOrNull { it.raw == raw }
    if (match == null) { append(label); return }
    appendResolved(label, match, linkColor, errorColor)
}

private fun AnnotatedString.Builder.appendResolved(label: String, link: NoteLink, linkColor: Color, errorColor: Color) {
    val tag: String
    val value: String
    val color: Color
    when (link.resolution.status) {
        LinkStatus.Resolved -> {
            val id = link.resolution.noteId
            if (id == null) { append(label); return }
            tag = NoteTag; value = "$id\n${link.fragment.orEmpty()}"; color = linkColor
        }
        LinkStatus.Ambiguous -> { tag = AmbiguousTag; value = link.raw; color = errorColor }
        LinkStatus.Missing -> { tag = MissingTag; value = link.target; color = errorColor }
        LinkStatus.External -> { tag = UrlTag; value = link.target; color = linkColor }
    }
    pushStringAnnotation(tag, value)
    pushStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    append(label)
    pop(); pop()
}

private fun imageLinks(text: String, links: List<NoteLink>): List<NoteLink> = links.filter { link ->
    link.isAsset && link.isEmbed && (
        text.contains("![[${link.raw}]]") ||
            text.contains("![${link.display.orEmpty()}](${link.raw})") ||
            text.contains("(${link.raw})")
        )
}

private fun removeImageSyntax(text: String, links: List<NoteLink>): String {
    val output = StringBuilder()
    var cursor = 0
    while (cursor < text.length) {
        val wikiEnd = if (text.startsWith("![[", cursor)) text.indexOf("]]", cursor + 3) else -1
        if (wikiEnd >= 0) {
            val raw = text.substring(cursor + 3, wikiEnd)
            val link = links.firstOrNull { it.raw == raw && it.isEmbed }
            if (link?.isAsset == false) output.append("[[").append(raw).append("]] ")
            cursor = wikiEnd + 2
            continue
        }
        if (text.startsWith("![", cursor)) {
            val labelEnd = text.indexOf(']', cursor + 2)
            val targetEnd = if (labelEnd >= 0 && text.getOrNull(labelEnd + 1) == '(') text.indexOf(')', labelEnd + 2) else -1
            if (targetEnd >= 0) { cursor = targetEnd + 1; continue }
        }
        output.append(text[cursor++])
    }
    return output.toString().trim()
}
