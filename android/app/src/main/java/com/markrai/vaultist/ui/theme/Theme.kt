package com.markrai.vaultist.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.markrai.vaultist.domain.SearchMode

data class VaultistExtendedColors(
    val browseMode: Color,
    val onBrowseMode: Color,
)

val LocalVaultistExtendedColors = staticCompositionLocalOf {
    VaultistExtendedColors(
        browseMode = Color.Unspecified,
        onBrowseMode = Color.Unspecified,
    )
}

object VaultistThemeColors {
    @Composable
    fun browseModeToggle(@Suppress("UNUSED_PARAMETER") mode: SearchMode): Color =
        LocalVaultistExtendedColors.current.browseMode

    @Composable
    fun onBrowseModeToggle(@Suppress("UNUSED_PARAMETER") mode: SearchMode): Color =
        LocalVaultistExtendedColors.current.onBrowseMode
}

@Composable
fun VaultistTheme(
    theme: AppColorTheme = AppColorTheme.Ruby,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(theme, darkTheme)
    val extended = VaultistExtendedColors(
        browseMode = palette.browseMode,
        onBrowseMode = palette.onBrowseMode,
    )
    val materialColors = if (darkTheme) {
        darkColors(
            primary = palette.primary,
            primaryVariant = palette.primaryVariant,
            secondary = palette.secondary,
            background = palette.background,
            surface = palette.surface,
            onPrimary = palette.onPrimary,
            onBackground = palette.onBackground,
            onSurface = palette.onSurface,
        )
    } else {
        lightColors(
            primary = palette.primary,
            primaryVariant = palette.primaryVariant,
            secondary = palette.secondary,
            background = palette.background,
            surface = palette.surface,
            onPrimary = palette.onPrimary,
            onBackground = palette.onBackground,
            onSurface = palette.onSurface,
        )
    }
    CompositionLocalProvider(LocalVaultistExtendedColors provides extended) {
        MaterialTheme(colors = materialColors, content = content)
    }
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
}
