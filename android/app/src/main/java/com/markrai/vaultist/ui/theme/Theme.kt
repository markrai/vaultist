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
import com.markrai.vaultist.domain.SearchMode

private val LightColors = lightColors(
    primary = Color(0xFFE0115F),
    primaryVariant = Color(0xFFB80E4C),
    secondary = Color(0xFF625B71),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

private val DarkColors = darkColors(
    primary = Color(0xFFFF6B8F),
    primaryVariant = Color(0xFFFF8FA8),
    secondary = Color(0xFFCCC2DC),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onPrimary = Color(0xFF3B0015),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
)

data class VaultistExtendedColors(
    val browseModeFiles: Color,
    val onBrowseModeFiles: Color,
    val browseModeContent: Color,
    val onBrowseModeContent: Color,
    val browseModeAsk: Color,
    val onBrowseModeAsk: Color,
)

private val LightExtendedColors = VaultistExtendedColors(
    browseModeFiles = Color(0xFFDE3163),
    onBrowseModeFiles = Color.White,
    browseModeContent = Color(0xFFDE3163),
    onBrowseModeContent = Color.White,
    browseModeAsk = Color(0xFFDE3163),
    onBrowseModeAsk = Color.White,
)

private val DarkExtendedColors = VaultistExtendedColors(
    browseModeFiles = Color(0xFFDE3163),
    onBrowseModeFiles = Color.White,
    browseModeContent = Color(0xFFDE3163),
    onBrowseModeContent = Color.White,
    browseModeAsk = Color(0xFFDE3163),
    onBrowseModeAsk = Color.White,
)

val LocalVaultistExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object VaultistThemeColors {
    @Composable
    fun browseModeToggle(mode: SearchMode): Color {
        val colors = LocalVaultistExtendedColors.current
        return when (mode) {
            SearchMode.Files -> colors.browseModeFiles
            SearchMode.Content -> colors.browseModeContent
            SearchMode.Ask -> colors.browseModeAsk
        }
    }

    @Composable
    fun onBrowseModeToggle(mode: SearchMode): Color {
        val colors = LocalVaultistExtendedColors.current
        return when (mode) {
            SearchMode.Files -> colors.onBrowseModeFiles
            SearchMode.Content -> colors.onBrowseModeContent
            SearchMode.Ask -> colors.onBrowseModeAsk
        }
    }
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
