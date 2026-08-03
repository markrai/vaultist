package com.vaultview.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import okhttp3.OkHttpClient

class ServerUrlTest {
    @Test fun normalizesHttpsAndLocalDevelopmentUrls() {
        assertEquals("https://vega.example.ts.net", normalizeServerUrl(" https://vega.example.ts.net/ "))
        assertEquals("http://10.0.2.2:8080", normalizeServerUrl("http://10.0.2.2:8080"))
    }

    @Test fun rejectsCredentialsPathsQueriesAndRemoteCleartext() {
        listOf(
            "http://vega.example.ts.net:8080",
            "https://user:pass@vega.example.ts.net",
            "https://vega.example.ts.net/api/v1",
            "https://vega.example.ts.net?q=x",
        ).forEach { value -> assertThrows(value, IllegalArgumentException::class.java) { normalizeServerUrl(value) } }
    }

    @Test fun constructsEncodedAssetUrlsWithoutExposingPathSyntax() {
        val api = VaultViewApi(OkHttpClient())
        assertEquals(
            "https://vega.example.ts.net/api/v1/assets/attachments%2Fmy%20image.png",
            api.assetUrl("https://vega.example.ts.net", "attachments/my image.png"),
        )
    }
}
