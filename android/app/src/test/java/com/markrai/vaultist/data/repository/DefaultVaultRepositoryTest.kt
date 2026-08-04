package com.markrai.vaultist.data.repository

import com.markrai.vaultist.data.api.VaultistApi
import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.domain.VaultError
import com.markrai.vaultist.domain.VaultResult
import com.markrai.vaultist.testutil.ApiFixtures
import com.markrai.vaultist.testutil.FakeServerUrlSettings
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultVaultRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: FakeServerUrlSettings
    private lateinit var repository: DefaultVaultRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        settings = FakeServerUrlSettings(baseUrl())
        repository = DefaultVaultRepository(VaultistApi(OkHttpClient()), settings)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getVaultAndListNotesParseSuccessResponses() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.VAULT))
        val vault = repository.getVault()
        assertTrue(vault is VaultResult.Success)
        assertEquals("Contract Vault", (vault as VaultResult.Success).value.name)

        server.enqueue(MockResponse().setBody(ApiFixtures.BROWSE_ROOT))
        val browse = repository.listNotes("", null)
        assertTrue(browse is VaultResult.Success)
        assertEquals(2, (browse as VaultResult.Success).value.items.size)
    }

    @Test
    fun testServerParsesNestedIndexFromStatus() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.STATUS))
        val result = repository.testServer(baseUrl())
        assertTrue(result is VaultResult.Success)
        assertEquals("ready", (result as VaultResult.Success).value.state)
        assertEquals(4, result.value.noteCount)
    }

    @Test
    fun getIndexStatusParsesTopLevelIndexState() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.INDEX_STATE))
        val result = repository.getIndexStatus()
        assertTrue(result is VaultResult.Success)
        assertEquals(1L, (result as VaultResult.Success).value.generation)
    }

    @Test
    fun refreshIndexRequiresAcceptedStatus() = runTest {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"status":"indexing"}"""))
        val accepted = repository.refreshIndex()
        assertTrue(accepted is VaultResult.Success)

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"indexing"}"""))
        val rejected = repository.refreshIndex()
        assertTrue(rejected is VaultResult.Failure)
        assertTrue((rejected as VaultResult.Failure).error is VaultError.Api)
    }

    @Test
    fun returnsNotConfiguredWhenServerUrlMissing() = runTest {
        settings.set(null)
        val result = repository.getVault()
        assertEquals(VaultResult.Failure(VaultError.NotConfigured), result)
    }

    @Test
    fun testServerRejectsInvalidUrl() = runTest {
        val result = repository.testServer("not a url")
        assertTrue(result is VaultResult.Failure)
        assertEquals(VaultError.InvalidServerUrl, (result as VaultResult.Failure).error)
    }

    @Test
    fun mapsApiErrorsFromErrorEnvelope() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody(ApiFixtures.noteNotFoundError()))
        val result = repository.getNote("Nope")
        assertTrue(result is VaultResult.Failure)
        val error = (result as VaultResult.Failure).error as VaultError.Api
        assertEquals("note_not_found", error.code)
    }

    @Test
    fun mapsMalformedSuccessJsonToInvalidResponse() = runTest {
        server.enqueue(MockResponse().setBody("""{"name":1}"""))
        val result = repository.getVault()
        assertTrue(result is VaultResult.Failure)
        assertTrue((result as VaultResult.Failure).error is VaultError.InvalidResponse)
    }

    @Test
    fun mapsConnectionFailuresToUnreachable() = runTest {
        val url = baseUrl()
        server.shutdown()
        val result = repository.getVault()
        assertTrue(result is VaultResult.Failure)
        assertEquals(VaultError.Unreachable, (result as VaultResult.Failure).error)
        settings.set(url)
    }

    @Test
    fun assetUrlReturnsNullWhenUnconfigured() {
        settings.set(null)
        assertNull(repository.assetUrl("pixel.png"))
    }

    @Test
    fun searchNotesParsesSearchPage() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.SEARCH))
        val result = repository.searchNotes("other", SearchMode.Files, null)
        assertTrue(result is VaultResult.Success)
        assertEquals("other", (result as VaultResult.Success).value.query)
    }

    private fun baseUrl(): String = "http://127.0.0.1:${server.port}"
}
