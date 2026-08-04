package com.vaultview.data.api

import com.vaultview.domain.SearchMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import kotlin.coroutines.resume

data class ApiPayload(val status: Int, val body: String, val etag: String?)

@Singleton
class VaultViewApi @Inject constructor(private val client: OkHttpClient) {
    suspend fun status(baseUrl: String): ApiPayload = get(baseUrl, listOf("status"))
    suspend fun vault(baseUrl: String): ApiPayload = get(baseUrl, listOf("vault"))
    suspend fun browse(baseUrl: String, folder: String, cursor: String? = null): ApiPayload =
        get(baseUrl, listOf("notes"), mapOf("folder" to folder, "limit" to "100", "cursor" to cursor))

    suspend fun search(baseUrl: String, query: String, mode: SearchMode = SearchMode.Files, cursor: String? = null): ApiPayload =
        get(baseUrl, listOf("search"), mapOf(
            "q" to query,
            "mode" to when (mode) {
                SearchMode.Files -> "files"
                SearchMode.Content -> "content"
                SearchMode.Ask -> "files"
            },
            "limit" to "100",
            "cursor" to cursor,
        ))

    suspend fun note(baseUrl: String, id: String): ApiPayload = get(baseUrl, listOf("notes", id))

    suspend fun backlinks(baseUrl: String, id: String): ApiPayload =
        get(baseUrl, listOf("notes", id, "backlinks"))

    suspend fun indexStatus(baseUrl: String): ApiPayload = get(baseUrl, listOf("index", "status"))

    suspend fun refresh(baseUrl: String): ApiPayload = request(
        Request.Builder().url(endpoint(baseUrl, listOf("index", "refresh"))).post(ByteArray(0).toRequestBody()).build()
    )

    fun assetUrl(baseUrl: String, id: String): String = endpoint(baseUrl, listOf("assets", id)).toString()

    private suspend fun get(baseUrl: String, segments: List<String>, query: Map<String, String?> = emptyMap()): ApiPayload {
        val builder = endpoint(baseUrl, segments).newBuilder()
        query.forEach { (name, value) -> if (!value.isNullOrBlank()) builder.addQueryParameter(name, value) }
        return request(Request.Builder().url(builder.build()).get().build())
    }

    private fun endpoint(baseUrl: String, segments: List<String>): HttpUrl {
        val root = normalizeServerUrl(baseUrl).toHttpUrlOrNull() ?: error("Invalid server URL")
        val builder = root.newBuilder().addPathSegments("api/v1")
        segments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private suspend fun request(request: Request): ApiPayload = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, exception: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(exception))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val payload = ApiPayload(it.code, it.body?.string().orEmpty(), it.header("ETag"))
                    if (continuation.isActive) continuation.resume(payload)
                }
            }
        })
    }
}

fun normalizeServerUrl(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    val parsed = trimmed.toHttpUrlOrNull() ?: throw IllegalArgumentException("Enter a valid HTTP or HTTPS URL")
    require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "Credentials are not allowed in the URL" }
    require(parsed.query == null && parsed.fragment == null) { "Query strings and fragments are not allowed" }
    require(parsed.encodedPath == "/" || parsed.encodedPath.isEmpty()) { "Enter the server root URL" }
    if (!parsed.isHttps) {
        require(parsed.host == "10.0.2.2" || parsed.host == "localhost" || parsed.host == "127.0.0.1") {
            "Use HTTPS for non-local servers"
        }
    }
    return parsed.newBuilder().encodedPath("/").build().toString().trimEnd('/')
}

fun parseApiError(payload: ApiPayload): Pair<String, String> = runCatching {
    val error = JSONObject(payload.body).getJSONObject("error")
    error.getString("code") to error.getString("message")
}.getOrElse { "http_${payload.status}" to "The server returned HTTP ${payload.status}" }
