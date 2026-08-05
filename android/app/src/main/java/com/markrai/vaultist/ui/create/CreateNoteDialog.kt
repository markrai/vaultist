package com.markrai.vaultist.ui.create

import androidx.compose.foundation.layout.Column
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.markrai.vaultist.domain.noteIdFromTitle

@Composable
fun CreateNoteDialog(
    folder: String,
    title: String,
    creating: Boolean,
    error: String?,
    onTitleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val preview = noteIdFromTitle(folder, title).leaf
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New note") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title") },
                    enabled = !creating,
                    singleLine = true,
                    modifier = Modifier,
                )
                if (!preview.isNullOrBlank()) {
                    Text(
                        text = preview + ".md",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier,
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreate, enabled = !creating && title.isNotBlank()) {
                Text(if (creating) "Creating…" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) { Text("Cancel") }
        },
    )
}
