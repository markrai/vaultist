package com.vaultview.ui.note

import androidx.lifecycle.SavedStateHandle
import com.vaultview.domain.Note
import com.vaultview.domain.VaultResult
import com.vaultview.testutil.FakeVaultRepository
import com.vaultview.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test fun exposesNoteAndHeadingFragmentState() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            noteResult = VaultResult.Success(Note("Folder/Note", "Folder/Note.md", "Note.md", "Note", emptyList(), emptyList(), emptyList(), emptyList(), "sha256:x", "# Note", null))
        }
        val viewModel = NoteViewModel(SavedStateHandle(mapOf("id" to "Folder/Note", "fragment" to "Part")), repository)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.loading)
        assertEquals("Folder/Note", viewModel.state.value.note?.id)
        assertEquals("Part", viewModel.fragment)
        assertEquals("https://vega.example.ts.net/api/v1/assets/image.png", viewModel.assetUrl("image.png"))
    }
}
