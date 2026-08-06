package com.markrai.vaultist.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
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
    onColorThemeChange: (AppColorTheme) -> Unit,
    onAppearanceChange: (AppAppearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Theme", style = MaterialTheme.typography.subtitle1)
        Row(
            Modifier.fillMaxWidth().testTag("theme_toggle_row"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ThemeOptionButton(
                label = "Ruby",
                selected = colorTheme == AppColorTheme.Ruby,
                onClick = { onColorThemeChange(AppColorTheme.Ruby) },
                modifier = Modifier.weight(1f).testTag("theme_ruby"),
            )
            ThemeOptionButton(
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
            ThemeOptionButton(
                label = "Light",
                selected = appearance == AppAppearance.Light,
                onClick = { onAppearanceChange(AppAppearance.Light) },
                modifier = Modifier.weight(1f).testTag("appearance_light"),
            )
            ThemeOptionButton(
                label = "Dark",
                selected = appearance == AppAppearance.Dark,
                onClick = { onAppearanceChange(AppAppearance.Dark) },
                modifier = Modifier.weight(1f).testTag("appearance_dark"),
            )
        }
    }
}

@Composable
private fun ThemeOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}
