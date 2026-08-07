package com.markrai.vaultist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markrai.vaultist.data.settings.ModifiedDatePreferences
import com.markrai.vaultist.data.settings.ThemePreferences
import com.markrai.vaultist.domain.ModifiedDateStyle
import com.markrai.vaultist.ui.components.LocalModifiedDateStyle
import com.markrai.vaultist.ui.markdown.LocalColorizedHeadings
import com.markrai.vaultist.ui.markdown.LocalHeadingColorPalette
import com.markrai.vaultist.ui.navigation.VaultistNavigation
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.HeadingColorPalette
import com.markrai.vaultist.ui.theme.VaultistTheme
import com.markrai.vaultist.ui.widget.OpenNoteFromWidget
import com.markrai.vaultist.ui.widget.WidgetIntents
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themePreferences: ThemePreferences
    @Inject lateinit var modifiedDatePreferences: ModifiedDatePreferences
    @Inject lateinit var openNoteFromWidget: OpenNoteFromWidget

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleWidgetIntent(intent)
        enableEdgeToEdge()
        setContent {
            val theme by themePreferences.colorTheme.collectAsStateWithLifecycle(
                initialValue = AppColorTheme.Ruby,
            )
            val appearance by themePreferences.appearance.collectAsStateWithLifecycle(
                initialValue = AppAppearance.Light,
            )
            val modifiedDateStyle by modifiedDatePreferences.style.collectAsStateWithLifecycle(
                initialValue = ModifiedDateStyle.Absolute,
            )
            val colorizedHeadings by themePreferences.colorizedHeadings.collectAsStateWithLifecycle(
                initialValue = false,
            )
            val headingColorPalette by themePreferences.headingColorPalette.collectAsStateWithLifecycle(
                initialValue = HeadingColorPalette.Classic,
            )
            VaultistTheme(
                theme = theme,
                darkTheme = appearance == AppAppearance.Dark,
            ) {
                CompositionLocalProvider(
                    LocalModifiedDateStyle provides modifiedDateStyle,
                    LocalColorizedHeadings provides colorizedHeadings,
                    LocalHeadingColorPalette provides headingColorPalette,
                ) {
                    Surface(
                        color = MaterialTheme.colors.background,
                        modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    ) {
                        VaultistNavigation(openNoteFromWidget = openNoteFromWidget)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val noteId = WidgetIntents.extractNoteId(intent) ?: return
        openNoteFromWidget.offer(noteId)
        intent?.removeExtra(WidgetIntents.EXTRA_NOTE_ID)
    }
}
