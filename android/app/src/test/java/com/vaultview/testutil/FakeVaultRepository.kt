package com.vaultview.testutil

import com.vaultview.data.repository.VaultRepository
import com.vaultview.domain.Backlink
import com.vaultview.domain.BrowsePage
import com.vaultview.domain.IndexState
import com.vaultview.domain.Note
import com.vaultview.domain.SearchPage
import com.vaultview.domain.SearchMode
import com.vaultview.domain.VaultMetadata
import com.vaultview.domain.VaultResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVaultRepository : VaultRepository {
    val configuredUrl = MutableStateFlow<String?>("https://vega.example.ts.net")
    override val serverUrl: Flow<String?> = configuredUrl
    var testResult: VaultResult<IndexState> = VaultResult.Success(IndexState("ready", 1, 1, 0, 0))
    var noteResult: VaultResult<Note> = VaultResult.Failure(com.vaultview.domain.VaultError.Api("note_not_found", "Missing"))
    var searchResult: VaultResult<SearchPage> = VaultResult.Success(SearchPage(emptyList(), null, ""))
    var lastSearchMode: SearchMode? = null
    var lastSearchQuery: String? = null
    var backlinksResult: VaultResult<List<Backlink>> = VaultResult.Success(emptyList())
    var savedUrl: String? = null

    override suspend fun testServer(url: String) = testResult
    override suspend fun saveServer(url: String) { savedUrl = url }
    override suspend fun getVault() = VaultResult.Success(VaultMetadata("Test", 1, 0, 1, true))
    override suspend fun listNotes(folder: String, cursor: String?) = VaultResult.Success(BrowsePage(emptyList(), null, folder))
    override suspend fun getNote(id: String) = noteResult
    override suspend fun searchNotes(query: String, mode: SearchMode, cursor: String?) = searchResult.also {
        lastSearchMode = mode
        lastSearchQuery = query
    }
    override suspend fun getBacklinks(id: String) = backlinksResult
    override suspend fun refreshIndex() = VaultResult.Success(Unit)
    override suspend fun getIndexStatus() = VaultResult.Success(IndexState("ready", 1, 1, 0, 0))
    override fun assetUrl(assetId: String) = "https://vega.example.ts.net/api/v1/assets/$assetId"
}
