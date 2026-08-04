package com.markrai.vaultist.data.genai

import com.markrai.vaultist.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

interface OnDeviceAskEnabled {
    val enabled: Boolean
}

@Singleton
class BuildConfigOnDeviceAskEnabled @Inject constructor() : OnDeviceAskEnabled {
    override val enabled: Boolean = BuildConfig.ENABLE_ON_DEVICE_ASK
}
