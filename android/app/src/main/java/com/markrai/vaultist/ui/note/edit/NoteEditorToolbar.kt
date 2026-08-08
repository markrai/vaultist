package com.markrai.vaultist.ui.note.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import com.markrai.vaultist.ui.theme.Spacing

@Composable
fun NoteEditorToolbar(
    onInsertCheckbox: () -> Unit,
    onInsertDateTime: () -> Unit,
    onInsertWikiLink: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        NoteEditorToolButton(
            onClick = onInsertCheckbox,
            contentDescription = "Insert checkbox",
            enabled = enabled,
        ) {
            Icon(Icons.Default.CheckBox, contentDescription = null, tint = MaterialTheme.colors.onPrimary)
        }
        NoteEditorToolButton(
            onClick = onInsertDateTime,
            contentDescription = "Insert date and time",
            enabled = enabled,
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colors.onPrimary)
        }
        NoteEditorToolButton(
            onClick = onInsertWikiLink,
            contentDescription = "Insert wiki link",
            enabled = enabled,
        ) {
            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colors.onPrimary)
        }
    }
}

@Composable
private fun NoteEditorToolButton(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (enabled) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
            ),
    ) {
        icon()
    }
}
