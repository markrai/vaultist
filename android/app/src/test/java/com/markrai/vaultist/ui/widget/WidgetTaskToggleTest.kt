package com.markrai.vaultist.ui.widget

import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.VaultError
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.testutil.FakeVaultRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTaskToggleTest {
    private val taskNote = Note(
        id = "Folder/Tasks",
        path = "Folder/Tasks.md",
        filename = "Tasks.md",
        title = "Tasks",
        aliases = emptyList(),
        headings = emptyList(),
        links = emptyList(),
        attachments = emptyList(),
        modifiedAt = "2026-01-01T00:00:00Z",
        size = 0L,
        revision = "sha256:abc",
        content = "- [ ] Open\n- [x] Done",
        error = null,
    )

    @Test
    fun togglesUncheckedToCheckedAndSaves() = runTest {
        val repository = FakeVaultRepository().apply {
            notesById = mapOf(taskNote.id to taskNote)
        }

        assertTrue(toggleWidgetTask(repository, taskNote.id, 1))

        assertEquals("sha256:abc", repository.lastUpdateRevision)
        assertEquals("- [x] Open\n- [x] Done", repository.lastUpdateContent)
    }

    @Test
    fun togglesCheckedToUncheckedAndSaves() = runTest {
        val repository = FakeVaultRepository().apply {
            notesById = mapOf(taskNote.id to taskNote)
        }

        assertTrue(toggleWidgetTask(repository, taskNote.id, 2))

        assertEquals("- [ ] Open\n- [ ] Done", repository.lastUpdateContent)
    }

    @Test
    fun returnsFalseWhenLineHasNoTaskMarker() = runTest {
        val note = taskNote.copy(content = "- plain item")
        val repository = FakeVaultRepository().apply {
            notesById = mapOf(note.id to note)
        }

        assertFalse(toggleWidgetTask(repository, note.id, 1))

        assertEquals(null, repository.lastUpdateContent)
    }

    @Test
    fun returnsFalseWhenNoteMissing() = runTest {
        val repository = FakeVaultRepository()

        assertFalse(toggleWidgetTask(repository, "Missing/Note", 1))

        assertEquals(null, repository.lastUpdateContent)
    }

    @Test
    fun returnsFalseOnUpdateFailure() = runTest {
        val repository = FakeVaultRepository().apply {
            notesById = mapOf(taskNote.id to taskNote)
            updateNoteResult = VaultResult.Failure(VaultError.Api("revision_conflict", "conflict"))
        }

        assertFalse(toggleWidgetTask(repository, taskNote.id, 1))

        assertEquals("- [x] Open\n- [x] Done", repository.lastUpdateContent)
    }
}
