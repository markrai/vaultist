package com.markrai.vaultist.ui.create

import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.testutil.MainDispatcherRule
import com.markrai.vaultist.ui.browser.PendingBrowseSync
import com.markrai.vaultist.ui.note.NoteOpenSeed
import com.markrai.vaultist.ui.note.PendingNoteSync
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
class CreateNoteViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private val repository = FakeVaultRepository()
    private val noteOpenSeed = NoteOpenSeed()
    private val pendingNoteSync = PendingNoteSync()
    private val pendingBrowseSync = PendingBrowseSync()

    private fun viewModel() = CreateNoteViewModel(
        repository,
        noteOpenSeed,
        pendingNoteSync,
        pendingBrowseSync,
    )

    @Test
    fun submitCreatesNoteOffersBrowseUpsert() = runTest {
        val viewModel = viewModel()
        viewModel.openDialog()
        viewModel.updateTitle("My Note")
        viewModel.submit("Folder")
        advanceUntilIdle()

        val drained = pendingBrowseSync.drain()
        assertEquals(1, drained.size)
        val upsert = drained.single() as com.markrai.vaultist.ui.browser.BrowseMutation.UpsertNote
        assertEquals("Folder/My Note", upsert.note.id)
    }

    @Test
    fun submitCreatesFolderOffersBrowseUpsert() = runTest {
        val viewModel = viewModel()
        viewModel.openDialog()
        viewModel.updateMode(CreateItemMode.Folder)
        viewModel.updateTitle("Ideas")
        viewModel.submit("Folder")
        advanceUntilIdle()

        val drained = pendingBrowseSync.drain()
        assertEquals(1, drained.size)
        val upsert = drained.single() as com.markrai.vaultist.ui.browser.BrowseMutation.UpsertFolder
        assertEquals("Folder/Ideas", upsert.folder.path)
    }

    @Test
    fun submitCreatesNoteSeedsOpenAndEmitsPendingNote() = runTest {
        val viewModel = viewModel()
        viewModel.openDialog()
        viewModel.updateTitle("My Note")
        viewModel.submit("Folder")
        advanceUntilIdle()

        assertEquals("Folder/My Note", repository.lastCreateId)
        assertEquals("", repository.lastCreateContent)
        val pending = viewModel.state.value.pendingOpenNote
        assertEquals("Folder/My Note", pending?.id)
        assertEquals("", pending?.content)
        assertEquals("Folder/My Note", noteOpenSeed.consume("Folder/My Note")?.id)
        assertFalse(viewModel.state.value.dialogVisible)
    }

    @Test
    fun submitKeepsDialogOpenOnValidationError() = runTest {
        val viewModel = viewModel()
        viewModel.openDialog()
        viewModel.submit("Folder")
        advanceUntilIdle()

        assertNull(repository.lastCreateId)
        assertTrue(viewModel.state.value.dialogVisible)
        assertEquals("Enter a valid title.", viewModel.state.value.error)
        assertNull(noteOpenSeed.consume("anything"))
    }

    @Test
    fun submitSurfacesApiFailure() = runTest {
        repository.createNoteResult = VaultResult.Failure(
            com.markrai.vaultist.domain.VaultError.Api("note_exists", "A note with this ID already exists"),
        )
        val viewModel = viewModel()
        viewModel.openDialog()
        viewModel.updateTitle("Existing")
        viewModel.submit("Folder")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.dialogVisible)
        assertEquals("A note with this ID already exists", viewModel.state.value.error)
        assertNull(viewModel.state.value.pendingOpenNote)
        assertNull(noteOpenSeed.consume("Folder/Existing"))
    }

    @Test
    fun submitCreatesFolderAndEmitsPendingFolder() = runTest {
        val viewModel = viewModel()
        viewModel.openDialog()
        viewModel.updateMode(CreateItemMode.Folder)
        viewModel.updateTitle("Ideas")
        viewModel.submit("Folder")
        advanceUntilIdle()

        assertEquals("Folder/Ideas", repository.lastCreateFolderPath)
        assertEquals("Folder/Ideas", viewModel.state.value.pendingCreatedFolder?.path)
        assertNull(viewModel.state.value.pendingOpenNote)
        assertFalse(viewModel.state.value.dialogVisible)
    }

    @Test
    fun submitFolderSurfacesApiFailure() = runTest {
        repository.createFolderResult = VaultResult.Failure(
            com.markrai.vaultist.domain.VaultError.Api("folder_exists", "A folder with this path already exists"),
        )
        val viewModel = viewModel()
        viewModel.openDialog()
        viewModel.updateMode(CreateItemMode.Folder)
        viewModel.updateTitle("Existing")
        viewModel.submit("Folder")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.dialogVisible)
        assertEquals("A folder with this path already exists", viewModel.state.value.error)
        assertNull(viewModel.state.value.pendingCreatedFolder)
    }

    @Test
    fun createMissingLinkCreatesNoteInSourceFolder() = runTest {
        val viewModel = viewModel()
        viewModel.createMissingLink("Projects/A", "Cake")
        advanceUntilIdle()

        assertEquals("Projects/Cake", repository.lastCreateId)
        assertEquals("", repository.lastCreateContent)
        assertEquals("Projects/Cake", viewModel.state.value.pendingOpenNote?.id)
        assertEquals("Projects/Cake", noteOpenSeed.consume("Projects/Cake")?.id)
        assertTrue(pendingNoteSync.consumeReload("Projects/A"))
    }

    @Test
    fun createMissingLinkSurfacesValidationError() = runTest {
        val viewModel = viewModel()
        viewModel.createMissingLink("Home", "")
        advanceUntilIdle()

        assertNull(repository.lastCreateId)
        assertEquals("Enter a valid title.", viewModel.state.value.error)
    }

    @Test
    fun createMissingLinkOpensExistingNoteOnConflict() = runTest {
        val existing = Note(
            id = "Projects/baba",
            path = "Projects/baba.md",
            filename = "baba.md",
            title = "baba",
            aliases = emptyList(),
            headings = emptyList(),
            links = emptyList(),
            attachments = emptyList(),
            modifiedAt = "2026-01-01T00:00:00Z",
            size = 0L,
            revision = "sha256:existing",
            content = "",
            error = null,
        )
        repository.createNoteResult = VaultResult.Failure(
            com.markrai.vaultist.domain.VaultError.Api("note_exists", "A note with this ID already exists"),
        )
        repository.notesById = mapOf("Projects/baba" to existing)
        val viewModel = viewModel()
        viewModel.createMissingLink("Projects/gaga", "baba")
        advanceUntilIdle()

        assertEquals("Projects/baba", viewModel.state.value.pendingOpenNote?.id)
        assertEquals("Projects/baba", noteOpenSeed.consume("Projects/baba")?.id)
        assertTrue(pendingNoteSync.consumeReload("Projects/gaga"))
    }
}
