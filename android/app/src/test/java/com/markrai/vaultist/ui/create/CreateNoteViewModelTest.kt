package com.markrai.vaultist.ui.create

import com.markrai.vaultist.domain.VaultResult
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
class CreateNoteViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private val repository = FakeVaultRepository()

    @Test
    fun submitCreatesNoteAndEmitsOpenId() = runTest {
        val viewModel = CreateNoteViewModel(repository)
        viewModel.openDialog()
        viewModel.updateTitle("My Note")
        viewModel.submit("Folder")
        advanceUntilIdle()

        assertEquals("Folder/My Note", repository.lastCreateId)
        assertEquals("# My Note\n\n", repository.lastCreateContent)
        assertEquals("Folder/My Note", viewModel.state.value.pendingOpenNoteId)
        assertFalse(viewModel.state.value.dialogVisible)
    }

    @Test
    fun submitKeepsDialogOpenOnValidationError() = runTest {
        val viewModel = CreateNoteViewModel(repository)
        viewModel.openDialog()
        viewModel.submit("Folder")
        advanceUntilIdle()

        assertNull(repository.lastCreateId)
        assertTrue(viewModel.state.value.dialogVisible)
        assertEquals("Enter a valid title.", viewModel.state.value.error)
    }

    @Test
    fun submitSurfacesApiFailure() = runTest {
        repository.createNoteResult = VaultResult.Failure(
            com.markrai.vaultist.domain.VaultError.Api("note_exists", "A note with this ID already exists"),
        )
        val viewModel = CreateNoteViewModel(repository)
        viewModel.openDialog()
        viewModel.updateTitle("Existing")
        viewModel.submit("Folder")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.dialogVisible)
        assertEquals("A note with this ID already exists", viewModel.state.value.error)
        assertNull(viewModel.state.value.pendingOpenNoteId)
    }
}
