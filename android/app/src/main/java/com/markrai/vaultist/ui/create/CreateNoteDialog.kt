package com.markrai.vaultist.ui.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.markrai.vaultist.domain.noteIdFromTitle
import kotlinx.coroutines.delay

@Composable
fun CreateNoteDialog(
    folder: String,
    mode: CreateItemMode,
    title: String,
    creating: Boolean,
    error: String?,
    onModeChange: (CreateItemMode) -> Unit,
    onTitleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val validation = noteIdFromTitle(folder, title)
    val preview = validation.leaf
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (mode) {
                    CreateItemMode.Note -> "New note"
                    CreateItemMode.Folder -> "New folder"
                },
            )
        },
        text = {
            Column {
                TabRow(selectedTabIndex = mode.ordinal) {
                    Tab(
                        selected = mode == CreateItemMode.Note,
                        onClick = { if (!creating) onModeChange(CreateItemMode.Note) },
                        text = { Text("Note") },
                    )
                    Tab(
                        selected = mode == CreateItemMode.Folder,
                        onClick = { if (!creating) onModeChange(CreateItemMode.Folder) },
                        text = { Text("Folder") },
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Name") },
                    enabled = !creating,
                    singleLine = true,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .focusRequester(focusRequester),
                )
                if (!preview.isNullOrBlank()) {
                    Text(
                        text = when (mode) {
                            CreateItemMode.Note -> preview + ".md"
                            CreateItemMode.Folder -> validation.id.orEmpty()
                        },
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp),
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
