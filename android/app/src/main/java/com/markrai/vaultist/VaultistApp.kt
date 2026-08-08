package com.markrai.vaultist

import android.app.Application
import com.markrai.vaultist.ui.markdown.SvgParserSecurity
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VaultistApp : Application() {
    override fun onCreate() {
        SvgParserSecurity.configure()
        super.onCreate()
    }
}
