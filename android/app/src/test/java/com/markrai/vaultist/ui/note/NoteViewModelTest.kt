package com.markrai.vaultist.ui.note

import androidx.lifecycle.SavedStateHandle
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.VaultError
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private val sampleNote = Note(
        id = "Folder/Note",
        path = "Folder/Note.md",
        filename = "Note.md",
        title = "Note",
        aliases = emptyList(),
        headings = emptyList(),
        links = emptyList(),
        attachments = emptyList(),
        modifiedAt = "2026-01-01T00:00:00Z",
        size = 0L,
        revision = "sha256:abc",
        content = "# Note",
        error = null,
    )

    @Test fun exposesNoteAndHeadingFragmentState() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
        }
        val viewModel = NoteViewModel(SavedStateHandle(mapOf("id" to "Folder/Note", "fragment" to "Part")), repository)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.loading)
        assertEquals("Folder/Note", viewModel.state.value.note?.id)
        assertEquals("Part", viewModel.fragment)
        assertEquals("https://vega.example.ts.net/api/v1/assets/image.png", viewModel.assetUrl("image.png"))
    }

    @Test fun saveUpdatesNoteWhenWriteSucceeds() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
            updateNoteResult = VaultResult.Success(sampleNote.copy(content = "# Updated", revision = "sha256:new"))
        }
        val viewModel = NoteViewModel(SavedStateHandle(mapOf("id" to "Folder/Note")), repository)
        advanceUntilIdle()
        viewModel.enterEdit()
        viewModel.updateDraft("# Updated")
        viewModel.save()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.editing)
        assertEquals("# Updated", viewModel.state.value.note?.content)
        assertEquals("sha256:abc", repository.lastUpdateRevision)
    }

    @Test fun saveSurfacesRevisionConflict() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
            updateNoteResult = VaultResult.Failure(VaultError.Api("revision_conflict", "conflict"))
        }
        val viewModel = NoteViewModel(SavedStateHandle(mapOf("id" to "Folder/Note")), repository)
        advanceUntilIdle()
        viewModel.enterEdit()
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.conflict)
        assertTrue(viewModel.state.value.editing)
    }
}
