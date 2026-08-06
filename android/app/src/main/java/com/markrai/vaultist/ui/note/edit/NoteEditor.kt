package com.markrai.vaultist.ui.note.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.ui.components.ErrorPanel
import com.markrai.vaultist.ui.note.ReadScrollMapping
import com.markrai.vaultist.ui.theme.Spacing

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NoteEditor(
    draft: NoteEditDraft,
    editorFocused: Boolean,
    wikiSuggestions: List<BrowseItem>,
    wikiSearching: Boolean,
    saving: Boolean,
    error: String?,
    initialPartialScrollOffsetPx: Int,
    onDraftChange: (NoteEditDraft) -> Unit,
    onEditorFocusChanged: (Boolean) -> Unit,
    onInsertDateTime: () -> Unit,
    onInsertWikiLink: () -> Unit,
    onWikiSuggestionSelected: (String) -> Unit,
    onRetrySave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val showEditorChrome = editorFocused && imeVisible
    val scrollState = rememberScrollState()
    val interactionSource = remember { MutableInteractionSource() }
    var initialScrollApplied by remember { mutableStateOf(false) }
    var pendingScrollTargetPx by remember { mutableIntStateOf(-1) }
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = draft.text,
                selection = TextRange(draft.selectionStart, draft.selectionEnd),
            ),
        )
    }
    LaunchedEffect(draft.text, draft.selectionStart, draft.selectionEnd) {
        val synced = TextFieldValue(
            text = draft.text,
            selection = TextRange(draft.selectionStart, draft.selectionEnd),
        )
        if (textFieldValue.text != synced.text || textFieldValue.selection != synced.selection) {
            textFieldValue = synced
        }
    }
        LaunchedEffect(pendingScrollTargetPx, scrollState.maxValue) {
            val target = pendingScrollTargetPx
            if (target < 0) return@LaunchedEffect
            scrollState.scrollTo(target.coerceIn(0, scrollState.maxValue))
            if (scrollState.maxValue >= target || target == 0) {
                pendingScrollTargetPx = -1
            }
        }

        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { value ->
                        textFieldValue = value
                        onDraftChange(
                            NoteEditDraft(
                                text = value.text,
                                selectionStart = value.selection.start,
                                selectionEnd = value.selection.end,
                            ),
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .onFocusChanged { onEditorFocusChanged(it.isFocused) },
                    enabled = !saving,
                    onTextLayout = { layout ->
                        if (initialScrollApplied) return@BasicTextField
                        initialScrollApplied = true
                        pendingScrollTargetPx = ReadScrollMapping.editScrollOffsetPx(
                            layout = layout,
                            characterOffset = draft.selectionStart,
                            partialScrollOffsetPx = initialPartialScrollOffsetPx,
                        )
                    },
                    textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    decorationBox = { innerTextField ->
                        TextFieldDefaults.OutlinedTextFieldDecorationBox(
                            value = textFieldValue.text,
                            innerTextField = innerTextField,
                            enabled = !saving,
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = interactionSource,
                            isError = false,
                            colors = TextFieldDefaults.outlinedTextFieldColors(),
                        )
                    },
                )
                if (error != null) {
                    ErrorPanel(
                        message = error,
                        modifier = Modifier.padding(Spacing.md),
                        onRetry = onRetrySave,
                    )
                }
            }
            if (showEditorChrome) {
                Column(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .imePadding()
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (wikiSuggestions.isNotEmpty() || wikiSearching) {
                        WikiLinkSuggestionList(
                            suggestions = wikiSuggestions,
                            searching = wikiSearching,
                            onSelect = onWikiSuggestionSelected,
                            modifier = Modifier.fillMaxWidth(0.92f),
                        )
                    }
                    Box(Modifier.background(MaterialTheme.colors.surface.copy(alpha = 0.94f))) {
                        NoteEditorToolbar(
                            onInsertDateTime = onInsertDateTime,
                            onInsertWikiLink = onInsertWikiLink,
                            enabled = !saving,
                        )
                    }
                }
            }
        }
    }

