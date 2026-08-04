package com.markrai.vaultist.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColors(
    primary = Color(0xFF6750A4),
    primaryVariant = Color(0xFF4F378B),
    secondary = Color(0xFF625B71),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

private val DarkColors = darkColors(
    primary = Color(0xFFD0BCFF),
    primaryVariant = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onPrimary = Color(0xFF381E72),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
)

data class VaultistExtendedColors(
    val browseModeToggle: Color,
    val onBrowseModeToggle: Color,
)

private val LightExtendedColors = VaultistExtendedColors(
    browseModeToggle = Color(0xFF2E5A3C),
    onBrowseModeToggle = Color.White,
)

private val DarkExtendedColors = VaultistExtendedColors(
    browseModeToggle = Color(0xFF4A8B5E),
    onBrowseModeToggle = Color(0xFFE8F5E9),
)

val LocalVaultistExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object VaultistThemeColors {
    val browseModeToggle: Color
        @Composable get() = LocalVaultistExtendedColors.current.browseModeToggle

    val onBrowseModeToggle: Color
        @Composable get() = LocalVaultistExtendedColors.current.onBrowseModeToggle
}

@Composable
fun VaultistTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalVaultistExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(colors = if (darkTheme) DarkColors else LightColors, content = content)
    }
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
}
