package com.markrai.vaultist.ui.note

import androidx.lifecycle.SavedStateHandle
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.VaultError
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.testutil.FakeNoteSharePreparer
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    private fun viewModel(
        repository: FakeVaultRepository = FakeVaultRepository(),
        sharePreparer: FakeNoteSharePreparer = FakeNoteSharePreparer(),
        handle: SavedStateHandle = SavedStateHandle(mapOf("id" to "Folder/Note")),
    ) = NoteViewModel(handle, repository, sharePreparer)

    @Test fun exposesNoteAndHeadingFragmentState() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
        }
        val viewModel = viewModel(
            repository = repository,
            handle = SavedStateHandle(mapOf("id" to "Folder/Note", "fragment" to "Part")),
        )
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
        val viewModel = viewModel(repository = repository)
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
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.enterEdit()
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.conflict)
        assertTrue(viewModel.state.value.editing)
    }

    @Test fun saveSurfacesNonConflictErrors() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
            updateNoteResult = VaultResult.Failure(VaultError.Api("note_write_failed", "Note could not be saved"))
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.enterEdit()
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.editing)
        assertFalse(viewModel.state.value.conflict)
        assertEquals("Note could not be saved", viewModel.state.value.error)
    }

    @Test fun sharePreparesLoadedNoteContent() = runTest(dispatcherRule.dispatcher) {
        val sharePreparer = FakeNoteSharePreparer()
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
        }
        val viewModel = viewModel(repository = repository, sharePreparer = sharePreparer)
        advanceUntilIdle()

        viewModel.share()
        advanceUntilIdle()

        assertEquals("Folder/Note", sharePreparer.lastNoteId)
        assertEquals("Note.md", sharePreparer.lastFilename)
        assertEquals("# Note", sharePreparer.lastContent)
        assertEquals("Note.md", viewModel.state.value.pendingShare?.filename)
        assertEquals("text/markdown", viewModel.state.value.pendingShare?.mimeType)
    }

    @Test fun shareUsesDraftContentWhileEditing() = runTest(dispatcherRule.dispatcher) {
        val sharePreparer = FakeNoteSharePreparer()
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
        }
        val viewModel = viewModel(repository = repository, sharePreparer = sharePreparer)
        advanceUntilIdle()
        viewModel.enterEdit()
        viewModel.updateDraft("# Draft")

        viewModel.share()
        advanceUntilIdle()

        assertEquals("# Draft", sharePreparer.lastContent)
    }

    @Test fun shareSurfacesPrepareFailure() = runTest(dispatcherRule.dispatcher) {
        val sharePreparer = FakeNoteSharePreparer().apply {
            failure = RuntimeException("disk full")
        }
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
        }
        val viewModel = viewModel(repository = repository, sharePreparer = sharePreparer)
        advanceUntilIdle()

        viewModel.share()
        advanceUntilIdle()

        assertEquals("Could not prepare this note for sharing.", viewModel.state.value.shareError)
        assertNull(viewModel.state.value.pendingShare)
    }
}
