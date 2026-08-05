package com.markrai.vaultist.ui

import com.markrai.vaultist.domain.VaultError

fun VaultError.userMessage(): String = when (this) {
    VaultError.NotConfigured -> "Configure a Vaultist server first."
    VaultError.Unreachable -> "The server could not be reached. Check Tailscale and the server URL."
    VaultError.InvalidServerUrl -> "Enter a valid server URL. Non-local servers must use HTTPS."
    is VaultError.Api -> when (code) {
        "revision_conflict" -> "This note changed on the server. Reload and try again."
        else -> message
    }
    is VaultError.InvalidResponse -> message
}
