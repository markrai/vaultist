package com.markrai.vaultist.ui.browser

import com.markrai.vaultist.di.config.BrowseUiConfig
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowseKind
import com.markrai.vaultist.domain.BrowsePage
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.BrowseSortMode
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.SearchPage
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.testutil.FakeBrowseSortPreferences
import com.markrai.vaultist.testutil.FakeVaultRepository
import com.markrai.vaultist.testutil.MainDispatcherRule
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

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private fun viewModel(
        repository: FakeVaultRepository = FakeVaultRepository(),
        sortPreferences: FakeBrowseSortPreferences = FakeBrowseSortPreferences(),
    ) = BrowserViewModel(repository, BrowseUiConfig(), PendingBrowseSync(), sortPreferences)

    @Test
    fun filesSearchDebouncesAndPopulatesResults() = runTest(dispatcherRule.dispatcher) {
        val item = BrowseItem(BrowseKind.Note, "Folder/Vega", "Vega.md", "Vega", "Folder/Vega.md", null)
        val repository = FakeVaultRepository().apply {
            searchResult = VaultResult.Success(SearchPage(listOf(item), null, "vega"))
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isSearchResults)

        viewModel.updateQuery("vega")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSearchResults)
        assertEquals(SearchMode.Files, repository.lastSearchMode)
        assertEquals("vega", repository.lastSearchQuery)
        assertEquals("Folder/Vega", viewModel.state.value.items.single().id)
    }

    @Test
    fun clearingFilesQueryRestoresBrowse() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.updateQuery("vega")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isSearchResults)

        viewModel.updateQuery("")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSearchResults)
        assertEquals("", viewModel.state.value.query)
    }

    @Test
    fun contentSearchRunsOnlyOnSubmit() = runTest(dispatcherRule.dispatcher) {
        val item = BrowseItem(BrowseKind.Note, "Notes/Idea", "Idea.md", "Idea", "Notes/Idea.md", null)
        val repository = FakeVaultRepository().apply {
            searchResult = VaultResult.Success(SearchPage(listOf(item), null, "secret"))
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.toggleSearchMode()
        advanceUntilIdle()
        assertEquals(SearchMode.Content, viewModel.state.value.searchMode)

        viewModel.updateQuery("secret")
        advanceTimeBy(500)
        advanceUntilIdle()
        assertNull(repository.lastSearchQuery)
        assertFalse(viewModel.state.value.isSearchResults)

        viewModel.submitSearch()
        advanceUntilIdle()
        assertEquals(SearchMode.Content, repository.lastSearchMode)
        assertEquals("secret", repository.lastSearchQuery)
        assertTrue(viewModel.state.value.isSearchResults)
        assertEquals("Notes/Idea", viewModel.state.value.items.single().id)
    }

    @Test
    fun includeCreatedNoteInsertsIntoCurrentFolder() = runTest(dispatcherRule.dispatcher) {
        val existing = BrowseItem(BrowseKind.Note, "Folder/Old", "Old.md", "Old", "Folder/Old.md", null)
        val repository = FakeVaultRepository().apply {
            listNotesResult = VaultResult.Success(BrowsePage(listOf(existing), null, "Folder"))
        }
        val viewModel = viewModel(repository)
        viewModel.openFolder("Folder")
        advanceUntilIdle()

        viewModel.includeCreatedNote(
            Note(
                id = "Folder/New",
                path = "Folder/New.md",
                filename = "New.md",
                title = "New",
                aliases = emptyList(),
                headings = emptyList(),
                links = emptyList(),
                attachments = emptyList(),
                modifiedAt = "2026-01-01T00:00:00Z",
                size = 1L,
                revision = "sha256:created",
                content = "# New\n\n",
                error = null,
            ),
        )

        assertEquals(listOf("Folder/New", "Folder/Old"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun afterNoteDeletedClearsSearchAndExcludesNote() = runTest(dispatcherRule.dispatcher) {
        val hit = BrowseItem(BrowseKind.Note, "Folder/Cake", "Cake.md", "Cake", "Folder/Cake.md", null)
        val repository = FakeVaultRepository().apply {
            searchResult = VaultResult.Success(SearchPage(listOf(hit), null, "cake"))
            listNotesResult = VaultResult.Success(
                BrowsePage(
                    listOf(
                        hit,
                        BrowseItem(BrowseKind.Note, "Folder/Other", "Other.md", "Other", "Folder/Other.md", null),
                    ),
                    null,
                    "Folder",
                ),
            )
        }
        val viewModel = viewModel(repository)
        viewModel.openFolder("Folder")
        advanceUntilIdle()
        viewModel.updateQuery("cake")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isSearchResults)

        viewModel.afterNoteDeleted("Folder/Cake")
        repository.listNotesResult = VaultResult.Success(
            BrowsePage(
                listOf(
                    BrowseItem(BrowseKind.Note, "Folder/Other", "Other.md", "Other", "Folder/Other.md", null),
                ),
                null,
                "Folder",
            ),
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSearchResults)
        assertEquals("", viewModel.state.value.query)
        assertEquals(listOf("Folder/Other"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun onReturnedToBrowseAppliesPendingDelete() = runTest(dispatcherRule.dispatcher) {
        val sync = PendingBrowseSync()
        val repository = FakeVaultRepository().apply {
            listNotesResult = VaultResult.Success(
                BrowsePage(
                    listOf(
                        BrowseItem(BrowseKind.Note, "Gone", "Gone.md", "Gone", "Gone.md", null),
                        BrowseItem(BrowseKind.Note, "Stay", "Stay.md", "Stay", "Stay.md", null),
                    ),
                    null,
                    "",
                ),
            )
        }
        val viewModel = BrowserViewModel(repository, BrowseUiConfig(), sync, FakeBrowseSortPreferences())
        advanceUntilIdle()
        sync.offerAfterDelete("Gone")
        viewModel.onReturnedToBrowse()
        repository.listNotesResult = VaultResult.Success(
            BrowsePage(
                listOf(BrowseItem(BrowseKind.Note, "Stay", "Stay.md", "Stay", "Stay.md", null)),
                null,
                "",
            ),
        )
        advanceUntilIdle()
        assertEquals(listOf("Stay"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun afterNoteDeletedKeepsNoteHiddenUntilIndexDropsIt() = runTest(dispatcherRule.dispatcher) {
        val deleted = BrowseItem(BrowseKind.Note, "Gone", "Gone.md", "Gone", "Gone.md", null)
        val stay = BrowseItem(BrowseKind.Note, "Stay", "Stay.md", "Stay", "Stay.md", null)
        val repository = FakeVaultRepository().apply {
            listNotesResult = VaultResult.Success(BrowsePage(listOf(deleted, stay), null, ""))
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.afterNoteDeleted("Gone")
        advanceUntilIdle()
        assertEquals(listOf("Stay"), viewModel.state.value.items.map { it.id })

        // Stale index still returns the deleted note; list must stay filtered.
        repository.listNotesResult = VaultResult.Success(BrowsePage(listOf(deleted, stay), null, ""))
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(listOf("Stay"), viewModel.state.value.items.map { it.id })

        repository.listNotesResult = VaultResult.Success(BrowsePage(listOf(stay), null, ""))
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(listOf("Stay"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun secondDeleteDoesNotResurrectFirstDeletedNote() = runTest(dispatcherRule.dispatcher) {
        val first = BrowseItem(BrowseKind.Note, "First", "First.md", "First", "First.md", null)
        val second = BrowseItem(BrowseKind.Note, "Second", "Second.md", "Second", "Second.md", null)
        val third = BrowseItem(BrowseKind.Note, "Third", "Third.md", "Third", "Third.md", null)
        val repository = FakeVaultRepository().apply {
            listNotesResult = VaultResult.Success(
                BrowsePage(listOf(first, second, third), null, ""),
            )
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.afterNoteDeleted("First")
        repository.listNotesResult = VaultResult.Success(
            BrowsePage(listOf(second, third), null, ""),
        )
        advanceUntilIdle()
        assertEquals(listOf("Second", "Third"), viewModel.state.value.items.map { it.id })

        viewModel.afterNoteDeleted("Second")
        repository.listNotesResult = VaultResult.Success(
            BrowsePage(listOf(first, second, third), null, ""),
        )
        advanceUntilIdle()
        assertEquals(listOf("Third"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun toggleSortModeSwitchesToModifiedDescAndReorders() = runTest(dispatcherRule.dispatcher) {
        val repository = FakeVaultRepository().apply {
            listNotesResult = VaultResult.Success(
                BrowsePage(
                    listOf(
                        BrowseItem(BrowseKind.Note, "Alpha", "Alpha.md", "Alpha", "Alpha.md", null, "2026-01-01T00:00:00Z"),
                        BrowseItem(BrowseKind.Note, "Zulu", "Zulu.md", "Zulu", "Zulu.md", null, "2026-01-03T00:00:00Z"),
                    ),
                    null,
                    "",
                ),
            )
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertEquals(BrowseSortMode.Alphabetical, viewModel.state.value.sortMode)
        assertEquals(listOf("Alpha", "Zulu"), viewModel.state.value.items.map { it.id })

        viewModel.toggleSortMode()
        advanceUntilIdle()
        assertEquals(BrowseSortMode.ModifiedDesc, viewModel.state.value.sortMode)
        assertEquals(listOf("Zulu", "Alpha"), viewModel.state.value.items.map { it.id })
    }

    @Test
    fun refreshPreservesActiveSortMode() = runTest(dispatcherRule.dispatcher) {
        val sortPreferences = FakeBrowseSortPreferences(BrowseSortMode.ModifiedDesc)
        val repository = FakeVaultRepository().apply {
            listNotesResult = VaultResult.Success(
                BrowsePage(
                    listOf(
                        BrowseItem(BrowseKind.Note, "A", "A.md", "A", "A.md", null, "2026-01-02T00:00:00Z"),
                    ),
                    null,
                    "",
                ),
            )
        }
        val viewModel = BrowserViewModel(
            repository,
            BrowseUiConfig(indexPollDelayMs = 1),
            PendingBrowseSync(),
            sortPreferences,
        )
        advanceUntilIdle()
        assertEquals(BrowseSortMode.ModifiedDesc, viewModel.state.value.sortMode)

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(BrowseSortMode.ModifiedDesc, viewModel.state.value.sortMode)
    }

    @Test
    fun delayedSortPreferenceRestoreAppliesChronologicalOrder() = runTest(dispatcherRule.dispatcher) {
        // Preference restore lands after browse starts but before listNotes returns —
        // the race that used to leave alphabetical order after a cold start.
        val sortPreferences = FakeBrowseSortPreferences(
            initialSortMode = BrowseSortMode.ModifiedDesc,
            restoreDelayMs = 10L,
        )
        val repository = FakeVaultRepository().apply {
            listNotesDelayMs = 50L
            listNotesResult = VaultResult.Success(
                BrowsePage(
                    listOf(
                        BrowseItem(BrowseKind.Note, "Alpha", "Alpha.md", "Alpha", "Alpha.md", null, "2026-01-01T00:00:00Z"),
                        BrowseItem(BrowseKind.Note, "Zulu", "Zulu.md", "Zulu", "Zulu.md", null, "2026-01-03T00:00:00Z"),
                    ),
                    null,
                    "",
                ),
            )
        }
        val viewModel = viewModel(repository, sortPreferences)
        advanceUntilIdle()

        assertEquals(BrowseSortMode.ModifiedDesc, viewModel.state.value.sortMode)
        assertEquals(listOf("Zulu", "Alpha"), viewModel.state.value.items.map { it.id })
    }
}
