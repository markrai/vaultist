package com.markrai.vaultist.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markrai.vaultist.R
import com.markrai.vaultist.domain.DateTimeInsertFormat
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.HeadingColorPalette
import com.markrai.vaultist.ui.theme.Spacing

@Composable
fun SetupScreen(onSaved: () -> Unit, onBack: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    SetupContent(
        state = state,
        onUrlChange = viewModel::updateUrl,
        onTest = viewModel::testConnection,
        onSave = viewModel::save,
        onEnableAskThinkingChange = viewModel::setEnableAskThinking,
        onColorThemeChange = viewModel::setColorTheme,
        onAppearanceChange = viewModel::setAppearance,
        onColorizedHeadingsChange = viewModel::setColorizedHeadings,
        onColorizeCheckboxStatusChange = viewModel::setColorizeCheckboxStatus,
        onHeadingColorPaletteChange = viewModel::setHeadingColorPalette,
        onRelativeModifiedDatesChange = viewModel::setRelativeModifiedDates,
        onDateTimeInsertFormatChange = viewModel::setDateTimeInsertFormat,
        onBack = onBack,
    )
}

@Composable
fun SetupContent(
    state: SetupUiState,
    onUrlChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onEnableAskThinkingChange: (Boolean) -> Unit,
    onColorThemeChange: (AppColorTheme) -> Unit,
    onAppearanceChange: (AppAppearance) -> Unit,
    onColorizedHeadingsChange: (Boolean) -> Unit,
    onColorizeCheckboxStatusChange: (Boolean) -> Unit,
    onHeadingColorPaletteChange: (HeadingColorPalette) -> Unit,
    onRelativeModifiedDatesChange: (Boolean) -> Unit,
    onDateTimeInsertFormatChange: (DateTimeInsertFormat) -> Unit,
    onBack: () -> Unit,
    initialTab: SetupTab = SetupTab.PREFERENCES,
) {
    var selectedTab by remember { mutableStateOf(initialTab) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SetupTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
            Spacer(Modifier.height(Spacing.md))
            when (selectedTab) {
                SetupTab.PREFERENCES -> {
                    PreferencesSetupPane(
                        modifier = Modifier.weight(1f),
                        relativeModifiedDates = state.relativeModifiedDates,
                        onRelativeModifiedDatesChange = onRelativeModifiedDatesChange,
                        dateTimeInsertFormat = state.dateTimeInsertFormat,
                        onDateTimeInsertFormatChange = onDateTimeInsertFormatChange,
                    )
                }
                SetupTab.CONNECT -> {
                    ConnectSetupPane(
                        modifier = Modifier.weight(1f),
                        state = state,
                        onUrlChange = onUrlChange,
                        onTest = onTest,
                        onSave = onSave,
                    )
                }
                SetupTab.THEME -> {
                    ThemeSetupPane(
                        modifier = Modifier.weight(1f),
                        colorTheme = state.colorTheme,
                        appearance = state.appearance,
                        colorizedHeadings = state.colorizedHeadings,
                        colorizeCheckboxStatus = state.colorizeCheckboxStatus,
                        headingColorPalette = state.headingColorPalette,
                        onColorThemeChange = onColorThemeChange,
                        onAppearanceChange = onAppearanceChange,
                        onColorizedHeadingsChange = onColorizedHeadingsChange,
                        onColorizeCheckboxStatusChange = onColorizeCheckboxStatusChange,
                        onHeadingColorPaletteChange = onHeadingColorPaletteChange,
                    )
                }
                SetupTab.ASK -> {
                    AskSetupPane(
                        modifier = Modifier.weight(1f),
                        enableAskThinking = state.enableAskThinking,
                        onEnableAskThinkingChange = onEnableAskThinkingChange,
                    )
                }
            }
        }
    }
}

@Composable
fun SetupTabBar(
    selectedTab: SetupTab,
    onTabSelected: (SetupTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier,
        backgroundColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.primary,
    ) {
        SetupTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.testTag("setup_tab_${tab.name.lowercase()}"),
                text = {
                    SetupTabLabel(
                        text = tab.label,
                        selected = selectedTab == tab,
                    )
                },
            )
        }
    }
}

@Composable
private fun SetupTabLabel(
    text: String,
    selected: Boolean,
) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        style = MaterialTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 13.sp),
        color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
    )
}
