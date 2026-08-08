package com.markrai.vaultist.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.markrai.vaultist.data.widget.NoteWidgetLoadResult
import com.markrai.vaultist.ui.theme.HeadingColorPalette

@Composable
fun NoteWidgetRoot(
    loadResult: NoteWidgetLoadResult,
    content: NoteWidgetContent?,
    noteId: String?,
    appWidgetId: Int,
    colorizedHeadings: Boolean,
    headingColorPalette: HeadingColorPalette = HeadingColorPalette.Classic,
) {
    val context = LocalContext.current
    val openAction = noteId?.let {
        GlanceModifier.clickable(actionStartActivity(WidgetIntents.openNote(context, it, appWidgetId)))
    } ?: GlanceModifier

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .then(openAction)
            .padding(12.dp),
    ) {
        when (loadResult) {
            NoteWidgetLoadResult.Unbound -> WidgetStatusText("Choose a note in widget settings.")
            NoteWidgetLoadResult.ServerNotConfigured -> WidgetStatusText("Open Vaultist to configure the server.")
            NoteWidgetLoadResult.NoteMissing -> WidgetStatusText("Note no longer exists.")
            NoteWidgetLoadResult.Offline -> WidgetStatusText("Vault server is unavailable.")
            is NoteWidgetLoadResult.Failure -> WidgetStatusText(loadResult.message)
            is NoteWidgetLoadResult.Content -> {
                val widgetContent = content ?: return@Box
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        text = widgetContent.title,
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 2,
                    )
                    LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                        items(widgetContent.blocks, itemId = { it.stableId }) { block ->
                            WidgetBlockRow(
                                block = block,
                                appWidgetId = appWidgetId,
                                colorizedHeadings = colorizedHeadings,
                                headingColorPalette = headingColorPalette,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetStatusText(message: String) {
    Text(
        text = message,
        style = TextStyle(
            color = GlanceTheme.colors.onBackground,
            fontSize = 14.sp,
        ),
    )
}

@Composable
private fun WidgetBlockRow(
    block: WidgetBlock,
    appWidgetId: Int,
    colorizedHeadings: Boolean,
    headingColorPalette: HeadingColorPalette,
) {
    when (block) {
        is WidgetBlock.Heading -> Text(
            text = block.text,
            modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
            style = TextStyle(
                color = widgetHeadingTextColor(
                    level = block.level,
                    colorizedHeadings = colorizedHeadings,
                    defaultColor = GlanceTheme.colors.onBackground,
                    palette = headingColorPalette,
                ),
                fontSize = headingSize(block.level),
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 4,
        )
        is WidgetBlock.Paragraph -> Text(
            text = block.text,
            modifier = GlanceModifier.padding(vertical = 2.dp),
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 14.sp,
            ),
            maxLines = 12,
        )
        is WidgetBlock.ListItem -> {
            val rowModifier = if (block.checked != null) {
                GlanceModifier.clickable(
                    actionRunCallback<ToggleTaskAction>(
                        actionParametersOf(
                            ToggleTaskAction.appWidgetIdKey to appWidgetId,
                            ToggleTaskAction.sourceLineKey to block.stableId.toInt(),
                        ),
                    ),
                )
            } else {
                GlanceModifier
            }
            Text(
                text = listPrefix(block.ordered, block.checked) + block.text,
                modifier = rowModifier.padding(vertical = 1.dp),
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 14.sp,
                ),
                maxLines = 8,
            )
        }
        is WidgetBlock.Quote -> Text(
            text = block.text,
            modifier = GlanceModifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 14.sp,
            ),
            maxLines = 8,
        )
        is WidgetBlock.Code -> Text(
            text = block.text,
            modifier = GlanceModifier.padding(vertical = 2.dp),
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            ),
            maxLines = 16,
        )
    }
}

private fun headingSize(level: Int) = when (level.coerceIn(1, 6)) {
    1 -> 18.sp
    2 -> 16.sp
    else -> 15.sp
}

private fun listPrefix(ordered: Boolean, checked: Boolean? = null): String = when {
    checked != null -> if (checked) "☑ " else "☐ "
    ordered -> "• "
    else -> "• "
}
