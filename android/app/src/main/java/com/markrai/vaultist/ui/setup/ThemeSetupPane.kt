package com.markrai.vaultist.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.markrai.vaultist.ui.theme.AppAppearance
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.HeadingColorPalette
import com.markrai.vaultist.ui.theme.Spacing

private val HeadingPaletteRows = listOf(
    HeadingColorPalette.Classic to HeadingColorPalette.ClassicReversed,
    HeadingColorPalette.Teal to HeadingColorPalette.TealReversed,
)

@Composable
fun ThemeSetupPane(
    colorTheme: AppColorTheme,
    appearance: AppAppearance,
    colorizedHeadings: Boolean,
    headingColorPalette: HeadingColorPalette,
    onColorThemeChange: (AppColorTheme) -> Unit,
    onAppearanceChange: (AppAppearance) -> Unit,
    onColorizedHeadingsChange: (Boolean) -> Unit,
    onHeadingColorPaletteChange: (HeadingColorPalette) -> Unit,
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("heading_palette_grid"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            HeadingPaletteRows.forEach { (forward, reversed) ->
                Row(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeadingPaletteSwatchOption(
                        palette = forward,
                        selected = headingColorPalette == forward,
                        onClick = { onHeadingColorPaletteChange(forward) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("heading_palette_${forward.id}"),
                    )
                    HeadingPaletteSwatchOption(
                        palette = reversed,
                        selected = headingColorPalette == reversed,
                        onClick = { onHeadingColorPaletteChange(reversed) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("heading_palette_${reversed.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadingPaletteSwatchOption(
    palette: HeadingColorPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.size(20.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colors.primary,
                unselectedColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            ),
        )
        HeadingColorSwatches(
            colors = palette.swatchColors(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HeadingColorSwatches(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .background(color),
            )
        }
    }
}
