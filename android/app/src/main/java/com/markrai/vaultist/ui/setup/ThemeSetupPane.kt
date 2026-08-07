package com.markrai.vaultist.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.Spacing

@Composable
fun ThemeSetupPane(
    colorTheme: AppColorTheme,
    appearance: AppAppearance,
    colorizedHeadings: Boolean,
    onColorThemeChange: (AppColorTheme) -> Unit,
    onAppearanceChange: (AppAppearance) -> Unit,
    onColorizedHeadingsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Color", style = MaterialTheme.typography.subtitle1)
        Row(
            Modifier.fillMaxWidth().testTag("theme_toggle_row"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SettingsOptionButton(
                label = "Ruby",
                selected = colorTheme == AppColorTheme.Ruby,
                onClick = { onColorThemeChange(AppColorTheme.Ruby) },
                modifier = Modifier.weight(1f).testTag("theme_ruby"),
            )
            SettingsOptionButton(
                label = "Forest",
                selected = colorTheme == AppColorTheme.Forest,
                onClick = { onColorThemeChange(AppColorTheme.Forest) },
                modifier = Modifier.weight(1f).testTag("theme_forest"),
            )
        }

        Text("Appearance", style = MaterialTheme.typography.subtitle1)
        Row(
            Modifier.fillMaxWidth().testTag("appearance_toggle_row"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SettingsOptionButton(
                label = "Light",
                selected = appearance == AppAppearance.Light,
                onClick = { onAppearanceChange(AppAppearance.Light) },
                modifier = Modifier.weight(1f).testTag("appearance_light"),
            )
            SettingsOptionButton(
                label = "Dark",
                selected = appearance == AppAppearance.Dark,
                onClick = { onAppearanceChange(AppAppearance.Dark) },
                modifier = Modifier.weight(1f).testTag("appearance_dark"),
            )
        }

        SettingsSwitchRow(
            title = "Colorized Headings",
            subtitle = "Use distinct colors for H1–H6 in note view.",
            checked = colorizedHeadings,
            onCheckedChange = onColorizedHeadingsChange,
            switchModifier = Modifier.testTag("colorized_headings_switch"),
        )
    }
}
