package com.markrai.vaultist.ui.markdown

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.markrai.vaultist.domain.LinkCandidate
import com.markrai.vaultist.domain.LinkResolution
import com.markrai.vaultist.domain.LinkStatus
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.NoteLink
import com.markrai.vaultist.ui.theme.Spacing

private const val NoteTag = "note"
private const val UrlTag = "url"
private const val MissingTag = "missing"
private const val AmbiguousTag = "ambiguous"
private val ListMarkerWidth = 28.dp
private val TaskCheckboxSize = 20.dp

/** Trailing punctuation often adjacent to bare URLs in prose. */
private val BareUrlTrailingTrim = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '\'', '"', '…')


@Composable
fun MarkdownRenderer(
    note: Note,
    fragment: String?,
    assetUrl: (String) -> String?,
    onOpenNote: (String, String?) -> Unit,
    onMissing: (String, Boolean) -> Unit,
    onAmbiguous: (String, List<LinkCandidate>) -> Unit,
    onOpenImage: (String) -> Unit,
    onReadScrollChanged: (sourceLine: Int, partialScrollOffsetPx: Int) -> Unit = { _, _ -> },
    canToggleTasks: Boolean = false,
    onTaskToggle: (sourceLine: Int) -> Unit = {},
    taskToggleInFlightLine: Int? = null,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(note.id, note.revision, note.content) { MarkdownDocumentParser.parse(note.content) }
    val listState = rememberLazyListState()
    val onReadScrollChangedState = rememberUpdatedState(onReadScrollChanged)
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val clearSelection = remember(focusManager, view) {
        {
            focusManager.clearFocus(force = true)
            view.clearFocus()
        }
    }
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
    LaunchedEffect(listState, blocks) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, partialScrollOffsetPx) ->
            val sourceLine = blocks.getOrNull(index)?.sourceLine ?: 1
            onReadScrollChangedState.value(sourceLine, partialScrollOffsetPx)
        }
    }
    SelectionContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clearSelectionOnUnhandledTap(clearSelection)
                .padding(horizontal = Spacing.md),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            itemsIndexed(blocks, key = { index, block -> "${block.sourceLine}:$index" }) { _, block ->
                when (block) {
                    is MarkdownBlock.Heading -> InlineText(
                        block.text,
                        note.links,
                        MarkdownTypography.heading(block.level, LocalColorizedHeadings.current),
                        onOpenNote,
                        onMissing,
                        onAmbiguous,
                        onClearSelection = clearSelection,
                        modifier = Modifier.padding(top = if (block.level <= 2) Spacing.md else Spacing.sm),
                    )
                    is MarkdownBlock.Paragraph -> ParagraphBlock(
                        block.text, note, assetUrl, onOpenNote, onMissing, onAmbiguous, onOpenImage, clearSelection,
                    )
                    is MarkdownBlock.ListItem -> MarkdownListItemRow(
                        block = block,
                        links = note.links,
                        canToggleTasks = canToggleTasks,
                        taskToggleInFlight = taskToggleInFlightLine != null,
                        onTaskToggle = onTaskToggle,
                        onOpenNote = onOpenNote,
                        onMissing = onMissing,
                        onAmbiguous = onAmbiguous,
                        onClearSelection = clearSelection,
                    )
                    is MarkdownBlock.Quote -> Row {
                        Box(Modifier.padding(end = Spacing.sm).background(MaterialTheme.colors.primary).heightIn(min = 24.dp).padding(horizontal = 2.dp))
                        InlineText(
                            block.text, note.links, MarkdownTypography.quote(),
                            onOpenNote, onMissing, onAmbiguous,
                            onClearSelection = clearSelection,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    is MarkdownBlock.Code -> Card(backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.06f), elevation = 0.dp) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .clearSelectionOnTap(clearSelection)
                                .padding(Spacing.md),
                        ) {
                            block.language?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.primary,
                                    modifier = Modifier.clearSelectionOnTap(clearSelection),
                                )
                            }
                            Text(
                                block.content,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier.clearSelectionOnTap(clearSelection),
                            )
                        }
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
    onMissing: (String, Boolean) -> Unit,
    onAmbiguous: (String, List<LinkCandidate>) -> Unit,
    onOpenImage: (String) -> Unit,
    onClearSelection: () -> Unit,
) {
    val images = remember(text, note.links) { imageLinks(text, note.links) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val displayText = removeImageSyntax(text, note.links)
        if (displayText.isNotBlank()) {
            InlineText(
                displayText, note.links, MarkdownTypography.body(), onOpenNote, onMissing, onAmbiguous,
                onClearSelection = onClearSelection,
            )
        }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .clickable(enabled = assetId != null) {
                                onClearSelection()
                                assetId?.let(onOpenImage)
                            },
                    )
                }
                LinkStatus.Ambiguous -> Text(
                    "Ambiguous image: ${link.target} (${link.resolution.candidates.joinToString { it.path }})",
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.clearSelectionOnTap(onClearSelection),
                )
                else -> Text(
                    "Missing image: ${link.target}",
                    color = MaterialTheme.colors.error,
                    modifier = Modifier
                        .clearSelectionOnTap(onClearSelection)
                        .clickable { onMissing(link.target, true) },
                )
            }
        }
    }
}

@Composable
fun InlineMarkdownText(
    text: String,
    links: List<NoteLink>,
    onOpenNote: (String, String?) -> Unit,
    onMissing: (String, Boolean) -> Unit,
    onAmbiguous: (String, List<LinkCandidate>) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
) {
    InlineText(
        text = text,
        links = links,
        style = style ?: MarkdownTypography.body(),
        onOpenNote = onOpenNote,
        onMissing = onMissing,
        onAmbiguous = onAmbiguous,
        onClearSelection = {},
        modifier = modifier,
    )
}

internal fun linksForBacklinkContext(
    context: String,
    targetNoteId: String,
    fragment: String?,
    display: String?,
    occurrenceKind: String,
): List<NoteLink> {
    val links = mutableListOf<NoteLink>()
    var cursor = 0
    while (cursor < context.length) {
        if (!context.startsWith("[[", cursor)) {
            cursor++
            continue
        }
        val end = context.indexOf("]]", cursor + 2)
        if (end <= cursor) break
        val raw = context.substring(cursor + 2, end)
        links += NoteLink(
            kind = occurrenceKind,
            raw = raw,
            target = raw.substringBefore('|').substringBefore('#').trim(),
            fragment = fragment,
            display = display,
            line = 1,
            column = cursor + 1,
            context = context,
            isEmbed = occurrenceKind == "wiki_embed",
            isAsset = false,
            resolution = LinkResolution(
                status = LinkStatus.Resolved,
                noteId = targetNoteId,
                assetId = null,
                candidates = emptyList(),
            ),
        )
        cursor = end + 2
    }
    return links
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun MarkdownListItemRow(
    block: MarkdownBlock.ListItem,
    links: List<NoteLink>,
    canToggleTasks: Boolean,
    taskToggleInFlight: Boolean,
    onTaskToggle: (sourceLine: Int) -> Unit,
    onOpenNote: (String, String?) -> Unit,
    onMissing: (String, Boolean) -> Unit,
    onAmbiguous: (String, List<LinkCandidate>) -> Unit,
    onClearSelection: () -> Unit,
) {
    val bodyStyle = MarkdownTypography.body()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .width(ListMarkerWidth)
                .heightIn(min = TaskCheckboxSize),
            contentAlignment = Alignment.CenterStart,
        ) {
            when (block.checked) {
                null -> Text(
                    if (block.ordered) "${block.number ?: 1}." else "•",
                    style = bodyStyle,
                    modifier = Modifier.clearSelectionOnTap(onClearSelection),
                )
                else -> CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                    Checkbox(
                        checked = block.checked,
                        onCheckedChange = { onTaskToggle(block.sourceLine) },
                        enabled = canToggleTasks && !taskToggleInFlight,
                        modifier = Modifier
                            .size(TaskCheckboxSize)
                            .clearSelectionOnTap(onClearSelection),
                    )
                }
            }
        }
        InlineText(
            block.text,
            links,
            bodyStyle,
            onOpenNote,
            onMissing,
            onAmbiguous,
            onClearSelection = onClearSelection,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InlineText(
    text: String,
    links: List<NoteLink>,
    style: TextStyle,
    onOpenNote: (String, String?) -> Unit,
    onMissing: (String, Boolean) -> Unit,
    onAmbiguous: (String, List<LinkCandidate>) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colors.isLight
    // Dedicated link blue — theme primary is too close to body text in dark mode.
    val linkColor = if (isLight) Color(0xFF0B57D0) else Color(0xFFA8C7FA)
    val errorColor = MaterialTheme.colors.error
    val annotated = remember(text, links, linkColor, errorColor) {
        annotatedInline(text, links, linkColor, errorColor)
    }
    val uriHandler = LocalUriHandler.current
    val openNoteState = rememberUpdatedState(onOpenNote)
    val missingState = rememberUpdatedState(onMissing)
    val ambiguousState = rememberUpdatedState(onAmbiguous)
    val clearSelectionState = rememberUpdatedState(onClearSelection)
    var layoutResult by remember(text, annotated) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = style.copy(
            color = if (style.color == Color.Unspecified) MaterialTheme.colors.onSurface else style.color,
        ),
        modifier = modifier.pointerInput(annotated, layoutResult) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val offset = layoutResult?.getOffsetForPosition(down.position)
                val annotation = offset?.let { annotated.linkAnnotationAt(it) }
                if (annotation == null) return@awaitEachGesture
                // Consume before SelectionContainer so web/wiki links stay tappable.
                down.consume()
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                up.consume()
                if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@awaitEachGesture
                }
                clearSelectionState.value()
                dispatchInlineLinkClick(
                    annotation = annotation,
                    links = links,
                    uriHandler = uriHandler,
                    onOpenNote = openNoteState.value,
                    onMissing = missingState.value,
                    onAmbiguous = ambiguousState.value,
                )
            }
        },
        onTextLayout = { layoutResult = it },
    )
}

private fun AnnotatedString.linkAnnotationAt(offset: Int): AnnotatedString.Range<String>? =
    getStringAnnotations(start = offset, end = offset).firstOrNull { range ->
        range.tag == NoteTag || range.tag == UrlTag || range.tag == MissingTag || range.tag == AmbiguousTag
    }

private fun dispatchInlineLinkClick(
    annotation: AnnotatedString.Range<String>,
    links: List<NoteLink>,
    uriHandler: UriHandler,
    onOpenNote: (String, String?) -> Unit,
    onMissing: (String, Boolean) -> Unit,
    onAmbiguous: (String, List<LinkCandidate>) -> Unit,
) {
    when (annotation.tag) {
        NoteTag -> {
            val pieces = annotation.item.split('\n', limit = 2)
            onOpenNote(pieces[0], pieces.getOrNull(1)?.takeIf(String::isNotBlank))
        }
        UrlTag -> runCatching { uriHandler.openUri(annotation.item) }
        MissingTag -> onMissing(annotation.item, false)
        AmbiguousTag -> {
            val match = links.firstOrNull { it.raw == annotation.item && it.resolution.status == LinkStatus.Ambiguous }
            onAmbiguous(match?.target ?: annotation.item, match?.resolution?.candidates.orEmpty())
        }
    }
}

private fun Modifier.clearSelectionOnTap(onClearSelection: () -> Unit): Modifier = pointerInput(onClearSelection) {
    detectTapGestures { onClearSelection() }
}

private fun Modifier.clearSelectionOnUnhandledTap(onClearSelection: () -> Unit): Modifier = pointerInput(onClearSelection) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            if (event.type != PointerEventType.Release) continue
            if (event.changes.any { it.isConsumed }) continue
            onClearSelection()
        }
    }
}

internal fun annotatedInline(
    text: String,
    links: List<NoteLink>,
    linkColor: Color,
    errorColor: Color,
): AnnotatedString = buildAnnotatedString {
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
                val targetEnd = if (targetStart >= 0) matchingMarkdownDestinationEnd(text, targetStart) else -1
                if (targetEnd > targetStart) {
                    val label = text.substring(cursor + 1, labelEnd)
                    val target = text.substring(targetStart, targetEnd).trim().substringBefore(' ').trim()
                    if (isWebUrl(target)) {
                        appendStyledLink(label, UrlTag, normalizeWebUrl(target), linkColor)
                    } else {
                        val match = links.firstOrNull { it.target == target || it.raw == target }
                        if (match != null) appendResolved(label, match, linkColor, errorColor)
                        else appendStyledLink(label, UrlTag, target, linkColor)
                    }
                    cursor = targetEnd + 1
                } else append(text[cursor++])
            }
            text[cursor] == '<' && looksLikeAngleAutolink(text, cursor) -> {
                val end = text.indexOf('>', cursor + 1)
                if (end > cursor) {
                    val inner = text.substring(cursor + 1, end).trim()
                    if (isWebUrl(inner)) {
                        val url = normalizeWebUrl(inner)
                        appendStyledLink(url, UrlTag, url, linkColor)
                        cursor = end + 1
                    } else append(text[cursor++])
                } else append(text[cursor++])
            }
            bareUrlPrefixLength(text, cursor) > 0 -> {
                val prefix = bareUrlPrefixLength(text, cursor)
                var end = cursor + prefix
                while (end < text.length && !text[end].isWhitespace() && text[end] != '<' && text[end] != '>') {
                    end++
                }
                while (end > cursor + prefix && text[end - 1] in BareUrlTrailingTrim) end--
                val raw = text.substring(cursor, end)
                val url = normalizeWebUrl(raw)
                appendStyledLink(raw, UrlTag, url, linkColor)
                cursor = end
            }
            else -> append(text[cursor++])
        }
    }
}

/** Find the `)` that closes a markdown destination, allowing balanced parentheses in the URL path. */
internal fun matchingMarkdownDestinationEnd(text: String, targetStart: Int): Int {
    var depth = 1
    var i = targetStart
    while (i < text.length) {
        when (text[i]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return i
            }
            ' ', '\n', '\r', '\t' -> if (depth == 1) {
                // Optional title after destination: [text](url "title")
                // Destination ends at whitespace when depth is still 1; find closing ).
                val close = text.indexOf(')', i)
                return if (close > i) close else -1
            }
        }
        i++
    }
    return -1
}

internal fun isWebUrl(target: String): Boolean {
    val value = target.trim()
    return value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("mailto:", ignoreCase = true) ||
        value.startsWith("//")
}

internal fun normalizeWebUrl(target: String): String {
    val value = target.trim()
    return if (value.startsWith("//")) "https:$value" else value
}

private fun looksLikeAngleAutolink(text: String, cursor: Int): Boolean {
    if (cursor + 1 >= text.length) return false
    val rest = text.substring(cursor + 1)
    return rest.startsWith("http://", ignoreCase = true) ||
        rest.startsWith("https://", ignoreCase = true) ||
        rest.startsWith("mailto:", ignoreCase = true) ||
        rest.startsWith("//")
}

private fun bareUrlPrefixLength(text: String, cursor: Int): Int {
    if (cursor > 0) {
        val prev = text[cursor - 1]
        if (prev.isLetterOrDigit() || prev == '/' || prev == '-' || prev == '_' || prev == '.') return 0
    }
    return when {
        text.startsWith("https://", cursor, ignoreCase = true) -> 8
        text.startsWith("http://", cursor, ignoreCase = true) -> 7
        text.startsWith("mailto:", cursor, ignoreCase = true) -> 7
        else -> 0
    }
}

private fun AnnotatedString.Builder.appendAnnotatedLink(
    label: String,
    raw: String,
    links: List<NoteLink>,
    linkColor: Color,
    errorColor: Color,
) {
    val match = links.firstOrNull { it.raw == raw }
    if (match == null) {
        if (isWebUrl(raw.substringBefore('|').substringBefore('#'))) {
            val target = raw.substringBefore('|').substringBefore('#').trim()
            appendStyledLink(label, UrlTag, normalizeWebUrl(target), linkColor)
        } else {
            val target = raw.substringBefore('|').substringBefore('#').trim()
            if (target.isNotEmpty()) {
                appendStyledLink(label, MissingTag, target, errorColor)
            } else {
                append(label)
            }
        }
        return
    }
    if (isWebUrl(match.target)) {
        appendStyledLink(label, UrlTag, normalizeWebUrl(match.target), linkColor)
        return
    }
    appendResolved(label, match, linkColor, errorColor)
}

private fun AnnotatedString.Builder.appendResolved(label: String, link: NoteLink, linkColor: Color, errorColor: Color) {
    when (link.resolution.status) {
        LinkStatus.Resolved -> {
            val id = link.resolution.noteId
            if (id == null) { append(label); return }
            appendStyledLink(label, NoteTag, "$id\n${link.fragment.orEmpty()}", linkColor)
        }
        LinkStatus.Ambiguous -> appendStyledLink(label, AmbiguousTag, link.raw, errorColor)
        LinkStatus.Missing -> appendStyledLink(label, MissingTag, link.target, errorColor)
        LinkStatus.External -> appendStyledLink(label, UrlTag, normalizeWebUrl(link.target), linkColor)
    }
}

private fun AnnotatedString.Builder.appendStyledLink(label: String, tag: String, value: String, color: Color) {
    pushStringAnnotation(tag, value)
    pushStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    append(label)
    pop()
    pop()
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
            val targetEnd = if (labelEnd >= 0 && text.getOrNull(labelEnd + 1) == '(') {
                matchingMarkdownDestinationEnd(text, labelEnd + 2)
            } else {
                -1
            }
            if (targetEnd >= 0) { cursor = targetEnd + 1; continue }
        }
        output.append(text[cursor++])
    }
    return output.toString().trim()
}
