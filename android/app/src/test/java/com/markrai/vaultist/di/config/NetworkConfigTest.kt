package com.markrai.vaultist.di.config

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkConfigTest {
    @Test
    fun defaultTimeoutsMapToOkHttpClient() {
        val config = NetworkConfig()
        val client = config.toOkHttpClient()

        assertEquals(Duration.ofSeconds(10).toMillis(), client.connectTimeoutMillis.toLong())
        assertEquals(Duration.ofSeconds(30).toMillis(), client.readTimeoutMillis.toLong())
        assertEquals(Duration.ofSeconds(10).toMillis(), client.writeTimeoutMillis.toLong())
        assertFalse(client.retryOnConnectionFailure)
    }

    @Test
    fun customTimeoutsAreApplied() {
        val config = NetworkConfig(
            connectTimeout = Duration.ofSeconds(5),
            readTimeout = Duration.ofSeconds(15),
            writeTimeout = Duration.ofSeconds(7),
            retryOnConnectionFailure = true,
        )
        val client = config.toOkHttpClient()

        assertEquals(5_000, client.connectTimeoutMillis.toLong())
        assertEquals(15_000, client.readTimeoutMillis.toLong())
        assertEquals(7_000, client.writeTimeoutMillis.toLong())
        assertEquals(true, client.retryOnConnectionFailure)
    }
}
