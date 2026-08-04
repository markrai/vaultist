package com.markrai.vaultist.di.config

import java.time.Duration
import okhttp3.OkHttpClient

data class NetworkConfig(
    val connectTimeout: Duration = Duration.ofSeconds(10),
    val readTimeout: Duration = Duration.ofSeconds(30),
    val writeTimeout: Duration = Duration.ofSeconds(10),
    val retryOnConnectionFailure: Boolean = false,
) {
    fun toOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeout)
        .readTimeout(readTimeout)
        .writeTimeout(writeTimeout)
        .retryOnConnectionFailure(retryOnConnectionFailure)
        .build()
}
