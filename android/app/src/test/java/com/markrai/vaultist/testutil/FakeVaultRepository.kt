package com.markrai.vaultist.testutil

import com.markrai.vaultist.data.repository.VaultRepository
import com.markrai.vaultist.domain.Backlink
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowsePage
import com.markrai.vaultist.domain.IndexState
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.SearchPage
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.VaultMetadata
import com.markrai.vaultist.domain.VaultResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVaultRepository : VaultRepository {
    val configuredUrl = MutableStateFlow<String?>("https://vega.example.ts.net")
    override val serverUrl: Flow<String?> = configuredUrl
    var testResult: VaultResult<IndexState> = VaultResult.Success(IndexState("ready", 1, 1, 0, 0))
    var noteResult: VaultResult<Note> = VaultResult.Failure(com.markrai.vaultist.domain.VaultError.Api("note_not_found", "Missing"))
    var notesById: Map<String, Note> = emptyMap()
    var searchResult: VaultResult<SearchPage> = VaultResult.Success(SearchPage(emptyList(), null, ""))
    var filesSearchResult: VaultResult<SearchPage>? = null
    var contentSearchResult: VaultResult<SearchPage>? = null
    /** Per-term substring responses: query(lowercase) -> notes that "contain" it. */
    var termIndex: Map<String, List<BrowseItem>> = emptyMap()
    var lastSearchMode: SearchMode? = null
    var lastSearchQuery: String? = null
    var searchQueries: MutableList<Pair<SearchMode, String>> = mutableListOf()
    var backlinksResult: VaultResult<List<Backlink>> = VaultResult.Success(emptyList())
    var savedUrl: String? = null

    override suspend fun testServer(url: String) = testResult
    override suspend fun saveServer(url: String) { savedUrl = url }
    override suspend fun getVault() = VaultResult.Success(VaultMetadata("Test", 1, 0, 1, true))
    override suspend fun listNotes(folder: String, cursor: String?) = VaultResult.Success(BrowsePage(emptyList(), null, folder))
    override suspend fun getNote(id: String): VaultResult<Note> =
        notesById[id]?.let { VaultResult.Success(it) } ?: noteResult

    override suspend fun searchNotes(query: String, mode: SearchMode, cursor: String?): VaultResult<SearchPage> {
        lastSearchMode = mode
        lastSearchQuery = query
        searchQueries += mode to query
        if (termIndex.isNotEmpty()) {
            val folded = query.lowercase()
            val items = termIndex.entries
                .filter { (term, _) -> folded == term || term.contains(folded) || folded.contains(term) }
                .flatMap { it.value }
                .distinctBy { it.id }
            return VaultResult.Success(SearchPage(items, null, query))
        }
        return when (mode) {
            SearchMode.Files -> filesSearchResult ?: searchResult
            SearchMode.Content -> contentSearchResult ?: searchResult
            SearchMode.Ask -> searchResult
        }
    }

    override suspend fun getBacklinks(id: String) = backlinksResult
    override suspend fun refreshIndex() = VaultResult.Success(Unit)
    override suspend fun getIndexStatus() = VaultResult.Success(IndexState("ready", 1, 1, 0, 0))
    override fun assetUrl(assetId: String) = "https://vega.example.ts.net/api/v1/assets/$assetId"
}
