package com.markrai.vaultist.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.markrai.vaultist.data.settings.ThemePreferences
import com.markrai.vaultist.data.widget.NoteWidgetRefresher
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.VaultistTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NoteWidgetConfigureActivity : ComponentActivity() {
    @Inject lateinit var themePreferences: ThemePreferences
    @Inject lateinit var noteWidgetRefresher: NoteWidgetRefresher

    private val viewModel: NoteWidgetConfigureViewModel by viewModels()

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        viewModel.initialize(appWidgetId)
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
                    NoteWidgetConfigureRoute(
                        viewModel = viewModel,
                        onConfirm = ::confirmSelection,
                    )
                }
            }
        }
    }

    private fun confirmSelection() {
        lifecycleScope.launch {
            val noteId = viewModel.confirmBinding() ?: return@launch
            val widgetId = appWidgetId
            // Configurable widgets do not get APPWIDGET_UPDATE; the activity must update Glance
            // state and call update() before finishing so a running session recomposes.
            val updated = noteWidgetRefresher.refreshWidget(widgetId, noteId)
            if (!updated) return@launch
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            )
            finish()
        }
    }
}
