package com.markrai.vaultist.data.share

import android.net.Uri

data class SharePayload(
    val uri: Uri,
    val filename: String,
    val mimeType: String,
)
