package com.markrai.vaultist.ui.share

import android.content.Context
import android.content.Intent
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.markrai.vaultist.data.share.SharePayload

fun launchShareNote(context: Context, payload: SharePayload) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = payload.mimeType
        putExtra(Intent.EXTRA_STREAM, payload.uri)
        putExtra(Intent.EXTRA_SUBJECT, payload.filename)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share note"))
}

@Composable
fun ShareNoteEffect(
    pendingShare: SharePayload?,
    shareError: String?,
    onConsumed: () -> Unit,
    onClearError: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(pendingShare) {
        pendingShare?.let { payload ->
            launchShareNote(context, payload)
            onConsumed()
        }
    }
    LaunchedEffect(shareError) {
        shareError?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearError()
        }
    }
}
