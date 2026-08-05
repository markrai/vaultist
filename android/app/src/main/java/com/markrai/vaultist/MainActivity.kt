package com.markrai.vaultist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.markrai.vaultist.ui.navigation.VaultistNavigation
import com.markrai.vaultist.ui.theme.VaultistTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaultistTheme {
                Surface(
                    color = Color.Black,
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                ) {
                    VaultistNavigation()
                }
            }
        }
    }
}
