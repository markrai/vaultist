package com.markrai.vaultist.data.api

import com.markrai.vaultist.domain.SearchMode
import com.markrai.vaultist.testutil.ApiFixtures
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultistApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: VaultistApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = VaultistApi(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun browseEncodesFolderAndCursor() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.BROWSE_ROOT))
        api.browse(baseUrl(), "Projects", "50")
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/notes", request.path?.substringBefore('?'))
        assertEquals("Projects", request.requestUrl?.queryParameter("folder"))
        assertEquals("50", request.requestUrl?.queryParameter("cursor"))
        assertEquals("100", request.requestUrl?.queryParameter("limit"))
    }

    @Test
    fun searchMapsContentAndAskModes() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.SEARCH))
        api.search(baseUrl(), "other", SearchMode.Content)
        assertEquals("content", server.takeRequest().requestUrl?.queryParameter("mode"))

        server.enqueue(MockResponse().setBody(ApiFixtures.SEARCH))
        api.search(baseUrl(), "other", SearchMode.Ask)
        assertEquals("files", server.takeRequest().requestUrl?.queryParameter("mode"))
    }

    @Test
    fun noteCapturesEtagAndEncodesId() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.NOTE).setHeader("ETag", "\"sha256:abc\""))
        val payload = api.note(baseUrl(), "Folder/Note")
        val request = server.takeRequest()
        assertEquals("/api/v1/notes/Folder%2FNote", request.path)
        assertEquals(200, payload.status)
        assertEquals("\"sha256:abc\"", payload.etag)
    }

    @Test
    fun updateNoteUsesPutIfMatchAndJsonBody() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.NOTE).setHeader("ETag", "\"sha256:updated\""))
        api.updateNote(baseUrl(), "Folder/Note", "sha256:abc", "# Updated")
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/notes/Folder%2FNote", request.path)
        assertEquals("\"sha256:abc\"", request.getHeader("If-Match"))
        assertEquals("""{"content":"# Updated"}""", request.body.readUtf8())
    }

    @Test
    fun refreshUsesPost() = runTest {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"status":"indexing"}"""))
        val payload = api.refresh(baseUrl())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/index/refresh", request.path)
        assertEquals(202, payload.status)
    }

    @Test
    fun statusAndBacklinksUseExpectedPaths() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.STATUS))
        api.status(baseUrl())
        assertEquals("/api/v1/status", server.takeRequest().path)

        server.enqueue(MockResponse().setBody(ApiFixtures.BACKLINKS))
        api.backlinks(baseUrl(), "Home")
        assertEquals("/api/v1/notes/Home/backlinks", server.takeRequest().path)
    }

    @Test
    fun indexStatusUsesDedicatedRoute() = runTest {
        server.enqueue(MockResponse().setBody(ApiFixtures.INDEX_STATE))
        api.indexStatus(baseUrl())
        assertEquals("/api/v1/index/status", server.takeRequest().path)
    }

    private fun baseUrl(): String = "http://127.0.0.1:${server.port}"
}
