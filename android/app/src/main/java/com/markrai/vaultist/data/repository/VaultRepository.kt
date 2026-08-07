package com.markrai.vaultist.data.repository

import com.markrai.vaultist.domain.Backlink
import com.markrai.vaultist.domain.BrowseItem
import com.markrai.vaultist.domain.BrowsePage
import com.markrai.vaultist.domain.IndexState
import com.markrai.vaultist.domain.Note
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.SearchPage
import com.markrai.vaultist.domain.VaultMetadata
import com.markrai.vaultist.domain.VaultResult
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    val serverUrl: Flow<String?>
    suspend fun testServer(url: String): VaultResult<IndexState>
    suspend fun saveServer(url: String)
    suspend fun getVault(): VaultResult<VaultMetadata>
    suspend fun listNotes(folder: String, cursor: String? = null): VaultResult<BrowsePage>
    suspend fun getNote(id: String): VaultResult<Note>
    suspend fun updateNote(id: String, revision: String, content: String): VaultResult<Note>
    suspend fun createNote(id: String, content: String): VaultResult<Note>
    suspend fun createFolder(path: String): VaultResult<BrowseItem>
    suspend fun deleteNote(id: String, revision: String): VaultResult<Unit>
    suspend fun searchNotes(query: String, mode: SearchMode = SearchMode.Files, cursor: String? = null): VaultResult<SearchPage>
    suspend fun getBacklinks(id: String): VaultResult<List<Backlink>>
    suspend fun refreshIndex(): VaultResult<Unit>
    suspend fun getIndexStatus(): VaultResult<IndexState>
    fun assetUrl(assetId: String): String?
}
