package com.markrai.vaultist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markrai.vaultist.data.settings.ThemePreferences
import com.markrai.vaultist.ui.navigation.VaultistNavigation
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.VaultistTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by themePreferences.colorTheme.collectAsStateWithLifecycle(
                initialValue = AppColorTheme.Ruby,
            )
            val appearance by themePreferences.appearance.collectAsStateWithLifecycle(
                initialValue = AppAppearance.Light,
            )
            VaultistTheme(
                theme = theme,
                darkTheme = appearance == AppAppearance.Dark,
            ) {
                Surface(
                    color = MaterialTheme.colors.background,
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                ) {
                    VaultistNavigation()
                }
            }
        }
    }
}
