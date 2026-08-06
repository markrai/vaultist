package com.markrai.vaultist.ui.note

import androidx.lifecycle.SavedStateHandle
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.VaultError
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.di.config.BrowseUiConfig
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.DateTimeInsertFormat
import com.markrai.vaultist.domain.IndexState
import com.markrai.vaultist.domain.SearchPage
import com.markrai.vaultist.testutil.FakeDateTimeInsertPreferences
import com.markrai.vaultist.testutil.FakeNoteSharePreparer
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.testutil.MainDispatcherRule
import com.markrai.vaultist.ui.browser.PendingBrowseSync
import com.markrai.vaultist.ui.note.edit.NoteEditDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
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
        noteOpenSeed: NoteOpenSeed = NoteOpenSeed(),
        pendingBrowseSync: PendingBrowseSync = PendingBrowseSync(),
        pendingNoteSync: PendingNoteSync = PendingNoteSync(),
        browseUiConfig: BrowseUiConfig = BrowseUiConfig(debounceMs = 50, indexPollDelayMs = 1),
        dateTimeInsertPreferences: FakeDateTimeInsertPreferences = FakeDateTimeInsertPreferences(),
    ) = NoteViewModel(
        handle,
        repository,
        sharePreparer,
        noteOpenSeed,
        pendingBrowseSync,
        pendingNoteSync,
        browseUiConfig,
        dateTimeInsertPreferences,
    )

    private fun draftAtEnd(text: String) = NoteEditDraft.atEnd(text)

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
            indexStatusResults = List(30) {
                VaultResult.Success(IndexState("indexing", 1, 1, 0, 0))
            }
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.enterEdit()
        viewModel.updateDraft(draftAtEnd("# Updated"))
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
        viewModel.updateDraft(draftAtEnd("# Draft"))

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

    @Test fun requestDeleteShowsDialogWhenWritable() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.requestDelete()
        assertTrue(viewModel.state.value.showDeleteDialog)
    }

    @Test fun confirmDeleteNavigatesOnSuccess() = runTest(dispatcherRule.dispatcher) {
        val pendingBrowseSync = PendingBrowseSync()
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
            deleteNoteResult = VaultResult.Success(Unit)
        }
        val viewModel = viewModel(repository = repository, pendingBrowseSync = pendingBrowseSync)
        advanceUntilIdle()
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.noteDeleted)
        assertEquals("sha256:abc", repository.lastDeleteRevision)
        assertEquals("Folder/Note", pendingBrowseSync.consumeAfterDelete())
    }

    @Test fun confirmDeleteSurfacesRevisionConflict() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
            deleteNoteResult = VaultResult.Failure(VaultError.Api("revision_conflict", "conflict"))
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.conflict)
        assertFalse(viewModel.state.value.noteDeleted)
    }

    @Test fun seededCreateOpensEditorWithoutGet() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Failure(VaultError.Api("note_not_found", "Note was not found"))
        }
        val seed = NoteOpenSeed().apply { offer(sampleNote) }
        val viewModel = viewModel(
            repository = repository,
            handle = SavedStateHandle(mapOf("id" to "Folder/Note", "edit" to "true")),
            noteOpenSeed = seed,
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.error)
        assertEquals("Folder/Note", viewModel.state.value.note?.id)
        assertTrue(viewModel.state.value.editing)
        assertEquals("# Note", viewModel.state.value.draft.text)
        assertEquals(0, viewModel.state.value.draft.selectionStart)
        assertEquals("sha256:abc", viewModel.state.value.baseRevision)
    }

    @Test fun enterEditUsesReadScrollAnchor() = runTest(dispatcherRule.dispatcher) {
        val content = "line one\nline two\nline three"
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote.copy(content = content))
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.onReadScrollChanged(2, 120)
        viewModel.enterEdit()
        assertEquals(9, viewModel.state.value.draft.selectionStart)
        assertEquals(120, viewModel.state.value.editorPartialScrollOffsetPx)
        assertEquals(content, viewModel.state.value.draft.text)
    }

    @Test fun onReturnedToScreenReloadsNoteAfterIndexReady() = runTest(dispatcherRule.dispatcher) {
        val stale = sampleNote
        val fresh = sampleNote.copy(content = "# Fresh links")
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(stale)
            indexStatusResults = listOf(
                VaultResult.Success(IndexState("indexing", 1, 1, 0, 0)),
                VaultResult.Success(IndexState("ready", 2, 2, 0, 0)),
            )
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        assertEquals("# Note", viewModel.state.value.note?.content)

        repository.notesById = mapOf("Folder/Note" to fresh)
        viewModel.onReturnedToScreen()
        advanceUntilIdle()

        assertEquals("# Fresh links", viewModel.state.value.note?.content)
        assertFalse(viewModel.state.value.loading)
    }

    @Test fun onReturnedToScreenSkipsWhileEditing() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
            indexStatusResults = listOf(VaultResult.Success(IndexState("ready", 1, 1, 0, 0)))
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        val callsAfterLoad = repository.getNoteCallCount
        viewModel.enterEdit()
        viewModel.onReturnedToScreen()
        advanceUntilIdle()
        assertEquals(callsAfterLoad, repository.getNoteCallCount)
    }

    @Test fun saveReconcilesLinksAfterIndexReady() = runTest(dispatcherRule.dispatcher) {
        val stale = sampleNote
        val saved = sampleNote.copy(content = "# Saved", revision = "sha256:new")
        val fresh = sampleNote.copy(content = "# Saved resolved", revision = "sha256:new")
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(stale)
            updateNoteResult = VaultResult.Success(saved)
            indexStatusResults = listOf(VaultResult.Success(IndexState("ready", 1, 1, 0, 0)))
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.enterEdit()
        viewModel.updateDraft(draftAtEnd("# Saved"))
        viewModel.save()
        repository.notesById = mapOf("Folder/Note" to fresh)
        advanceUntilIdle()
        assertEquals("# Saved resolved", viewModel.state.value.note?.content)
    }

    @Test fun insertWikiLinkStartPlacesCursorAfterOpener() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.enterEdit()
        viewModel.insertWikiLinkStart()
        assertEquals("[[# Note", viewModel.state.value.draft.text)
        assertEquals(2, viewModel.state.value.draft.selectionStart)
    }

    @Test fun insertDateTimeInsertsFormattedStampAtCursor() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
        }
        val viewModel = viewModel(
            repository = repository,
            dateTimeInsertPreferences = FakeDateTimeInsertPreferences(DateTimeInsertFormat.IsoDateTime),
        )
        advanceUntilIdle()
        viewModel.enterEdit()
        val expectedStamp = DateTimeInsertFormat.IsoDateTime.format()
        viewModel.insertDateTime()
        assertEquals("$expectedStamp# Note", viewModel.state.value.draft.text)
    }

    @Test fun wikiSuggestionsDebounceFilesSearchWhileTypingLink() = runTest(dispatcherRule.dispatcher) {
        val hit = BrowseItem(BrowseKind.Note, "Folder/Other", "Other.md", "Other", "Folder/Other.md", null)
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
            filesSearchResult = VaultResult.Success(SearchPage(listOf(hit), null, "oth"))
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.enterEdit()
        viewModel.updateDraft(NoteEditDraft("See [[Oth", 9, 9))
        advanceTimeBy(50)
        advanceUntilIdle()
        assertEquals(listOf("Folder/Other"), viewModel.state.value.wikiSuggestions.map { it.id })
    }

    @Test fun applyWikiSuggestionCompletesOpenWikiLink() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(sampleNote)
        }
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        viewModel.enterEdit()
        viewModel.updateDraft(NoteEditDraft("See [[Oth", 9, 9))
        viewModel.applyWikiSuggestion("Folder/Other")
        assertEquals("See [[Folder/Other]]", viewModel.state.value.draft.text)
    }
}
