package com.markrai.vaultist.data.widget

import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.VaultError
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.testutil.FakeNoteWidgetStore
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteWidgetLoaderTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private lateinit var preferences: FakeNoteWidgetStore
    private lateinit var repository: FakeVaultRepository
    private lateinit var loader: NoteWidgetLoader

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
        size = 1L,
        revision = "sha256:abc",
        content = "# Note",
        error = null,
    )

    @Before
    fun setUp() {
        preferences = FakeNoteWidgetStore()
        repository = FakeVaultRepository()
        loader = NoteWidgetLoader(repository, preferences)
    }

    @Test
    fun unboundWidgetDoesNotCallRepository() = runTest(dispatcherRule.dispatcher) {
        val result = loader.load(42)
        assertEquals(NoteWidgetLoadResult.Unbound, result)
        assertEquals(0, repository.getNoteCallCount)
    }

    @Test
    fun missingServerDoesNotCallRepository() = runTest(dispatcherRule.dispatcher) {
        repository.configuredUrl.value = null
        preferences.setBinding(1, "Folder/Note")
        val result = loader.load(1)
        assertEquals(NoteWidgetLoadResult.ServerNotConfigured, result)
        assertEquals(0, repository.getNoteCallCount)
    }

    @Test
    fun loadsBoundNoteWithCanonicalId() = runTest(dispatcherRule.dispatcher) {
        repository.notesById = mapOf("Folder/Note" to sampleNote)
        preferences.setBinding(7, "Folder/Note")
        val result = loader.load(7, "Folder/Note")
        assertTrue(result is NoteWidgetLoadResult.Content)
        assertEquals("Folder/Note", (result as NoteWidgetLoadResult.Content).note.id)
        assertEquals(1, repository.getNoteCallCount)
    }

    @Test
    fun noteMissingMapsToResult() = runTest(dispatcherRule.dispatcher) {
        preferences.setBinding(1, "missing")
        repository.noteResult = VaultResult.Failure(VaultError.Api("note_not_found", "gone"))
        val result = loader.load(1)
        assertEquals(NoteWidgetLoadResult.NoteMissing, result)
    }

    @Test
    fun offlineMapsToResult() = runTest(dispatcherRule.dispatcher) {
        preferences.setBinding(1, "Folder/Note")
        repository.noteResult = VaultResult.Failure(VaultError.Unreachable)
        val result = loader.load(1)
        assertEquals(NoteWidgetLoadResult.Offline, result)
    }
}
