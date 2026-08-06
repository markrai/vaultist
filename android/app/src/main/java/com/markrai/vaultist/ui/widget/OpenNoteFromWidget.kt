package com.markrai.vaultist.ui.widget

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class OpenNoteFromWidget @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(noteId: String) {
        _pending.value = noteId
    }

    fun consume(): String? {
        val noteId = _pending.value
        _pending.value = null
        return noteId
    }
}
