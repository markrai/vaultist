package com.markrai.vaultist.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.ModifiedDateStyle
import com.markrai.vaultist.ui.theme.Spacing

val LocalModifiedDateStyle = staticCompositionLocalOf { ModifiedDateStyle.Absolute }

@Composable
fun ErrorPanel(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(message, color = MaterialTheme.colors.error)
        if (onRetry != null) Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun NoteResultCard(
    item: BrowseItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    dateStyle: ModifiedDateStyle = LocalModifiedDateStyle.current,
) {
    val modifiedLabel = formatModifiedAt(item.modifiedAt, dateStyle)
    Card(modifier.fillMaxWidth().clickable(onClick = onClick), elevation = Spacing.xs) {
        Column(
            Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            modifiedLabel?.let { label ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
            Text(
                item.title ?: item.name,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.error?.let { Text(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption) }
        }
    }
}
