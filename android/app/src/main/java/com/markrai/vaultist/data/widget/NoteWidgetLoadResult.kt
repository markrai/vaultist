package com.markrai.vaultist.data.widget

import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.VaultError
import com.markrai.vaultist.domain.VaultResult

sealed interface NoteWidgetLoadResult {
    data object Unbound : NoteWidgetLoadResult
    data object ServerNotConfigured : NoteWidgetLoadResult
    data object NoteMissing : NoteWidgetLoadResult
    data object Offline : NoteWidgetLoadResult
    data class Content(val note: Note) : NoteWidgetLoadResult
    data class Failure(val message: String) : NoteWidgetLoadResult
}

internal fun VaultError.toWidgetLoadResult(): NoteWidgetLoadResult = when (this) {
    VaultError.NotConfigured -> NoteWidgetLoadResult.ServerNotConfigured
    VaultError.Unreachable -> NoteWidgetLoadResult.Offline
    is VaultError.Api -> when (code) {
        "note_not_found" -> NoteWidgetLoadResult.NoteMissing
        else -> NoteWidgetLoadResult.Failure(message.take(120))
    }
    VaultError.InvalidServerUrl -> NoteWidgetLoadResult.Failure("Invalid server URL.")
    is VaultError.InvalidResponse -> NoteWidgetLoadResult.Failure(message.take(120))
}
