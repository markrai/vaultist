package com.vaultview.data.repository

import com.vaultview.domain.Backlink
import com.vaultview.domain.BrowsePage
import com.vaultview.domain.IndexState
import com.vaultview.domain.Note
import com.vaultview.domain.SearchPage
import com.vaultview.domain.VaultMetadata
import com.vaultview.domain.VaultResult
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    val serverUrl: Flow<String?>
    suspend fun testServer(url: String): VaultResult<IndexState>
    suspend fun saveServer(url: String)
    suspend fun getVault(): VaultResult<VaultMetadata>
    suspend fun listNotes(folder: String, cursor: String? = null): VaultResult<BrowsePage>
    suspend fun getNote(id: String): VaultResult<Note>
    suspend fun searchNotes(query: String, mode: SearchMode = SearchMode.Files, cursor: String? = null): VaultResult<SearchPage>
    suspend fun getBacklinks(id: String): VaultResult<List<Backlink>>
    suspend fun refreshIndex(): VaultResult<Unit>
    suspend fun getIndexStatus(): VaultResult<IndexState>
    fun assetUrl(assetId: String): String?
}
