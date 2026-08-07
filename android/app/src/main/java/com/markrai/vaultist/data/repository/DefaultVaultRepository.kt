package com.markrai.vaultist.data.repository

import com.markrai.vaultist.data.api.ApiPayload
import com.markrai.vaultist.data.api.VaultistApi
import com.markrai.vaultist.data.api.normalizeServerUrl
import com.markrai.vaultist.data.api.parseApiError
import com.markrai.vaultist.data.api.toBacklinks
import com.markrai.vaultist.data.api.toBrowseItem
import com.markrai.vaultist.data.api.toBrowsePage
import com.markrai.vaultist.data.api.toIndexState
import com.markrai.vaultist.data.api.toNote
import com.markrai.vaultist.data.api.toSearchPage
import com.markrai.vaultist.data.api.toVaultMetadata
import com.markrai.vaultist.data.settings.ServerUrlSettings
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.VaultError
import com.markrai.vaultist.domain.VaultResult
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject

@Singleton
class DefaultVaultRepository @Inject constructor(
    private val api: VaultistApi,
    private val settings: ServerUrlSettings,
) : VaultRepository {
    private val currentServerUrl = AtomicReference<String?>(null)
    override val serverUrl = settings.serverUrl.onEach(currentServerUrl::set)

    override suspend fun testServer(url: String) = result {
        val normalized = normalizeServerUrl(url)
        val payload = api.status(normalized).requireSuccess()
        JSONObject(payload.body).getJSONObject("index").toIndexState()
    }

    override suspend fun saveServer(url: String) {
        val normalized = normalizeServerUrl(url)
        settings.saveServerUrl(normalized)
        currentServerUrl.set(normalized)
    }

    override suspend fun getVault() = configuredResult { base ->
        JSONObject(api.vault(base).requireSuccess().body).toVaultMetadata()
    }

    override suspend fun listNotes(folder: String, cursor: String?) = configuredResult { base ->
        JSONObject(api.browse(base, folder, cursor).requireSuccess().body).toBrowsePage()
    }

    override suspend fun getNote(id: String) = configuredResult { base ->
        JSONObject(api.note(base, id).requireSuccess().body).toNote()
    }

    override suspend fun updateNote(id: String, revision: String, content: String) = configuredResult { base ->
        JSONObject(api.updateNote(base, id, revision, content).requireSuccess().body).toNote()
    }

    override suspend fun createNote(id: String, content: String) = configuredResult { base ->
        JSONObject(api.createNote(base, id, content).requireSuccess().body).toNote()
    }

    override suspend fun createFolder(path: String) = configuredResult { base ->
        JSONObject(api.createFolder(base, path).requireSuccess().body).toBrowseItem()
    }

    override suspend fun deleteNote(id: String, revision: String) = configuredResult { base ->
        api.deleteNote(base, id, revision).requireSuccess()
        Unit
    }

    override suspend fun searchNotes(query: String, mode: SearchMode, cursor: String?) = configuredResult { base ->
        JSONObject(api.search(base, query, mode, cursor).requireSuccess().body).toSearchPage()
    }

    override suspend fun getBacklinks(id: String) = configuredResult { base ->
        JSONObject(api.backlinks(base, id).requireSuccess().body).toBacklinks()
    }

    override suspend fun refreshIndex() = configuredResult { base ->
        api.refresh(base).requireSuccess(accepted = true)
        Unit
    }

    override suspend fun getIndexStatus() = configuredResult { base ->
        JSONObject(api.indexStatus(base).requireSuccess().body).toIndexState()
    }

    override fun assetUrl(assetId: String): String? {
        val base = currentServerUrl.get() ?: return null
        return api.assetUrl(base, assetId)
    }

    private suspend fun <T> configuredResult(block: suspend (String) -> T): VaultResult<T> {
        val base = settings.serverUrl.first() ?: return VaultResult.Failure(VaultError.NotConfigured)
        currentServerUrl.set(base)
        return result { block(base) }
    }

    private suspend fun <T> result(block: suspend () -> T): VaultResult<T> = try {
        VaultResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IllegalArgumentException) {
        VaultResult.Failure(VaultError.InvalidServerUrl)
    } catch (_: IOException) {
        VaultResult.Failure(VaultError.Unreachable)
    } catch (error: ApiException) {
        VaultResult.Failure(VaultError.Api(error.code, error.safeMessage))
    } catch (_: Exception) {
        VaultResult.Failure(VaultError.InvalidResponse("The server response was not valid Vaultist JSON"))
    }
}

private fun ApiPayload.requireSuccess(accepted: Boolean = false): ApiPayload {
    if (status in 200..299 && (!accepted || status == 202)) return this
    val (code, message) = parseApiError(this)
    throw ApiException(code, message)
}

private class ApiException(val code: String, val safeMessage: String) : Exception()
