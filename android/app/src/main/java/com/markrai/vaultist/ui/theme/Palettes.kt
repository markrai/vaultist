package com.markrai.vaultist.ui.theme

import androidx.compose.ui.graphics.Color

private val lightSurfaces = VaultistPalette(
    primary = Color.Unspecified,
    primaryVariant = Color.Unspecified,
    secondary = Color(0xFF625B71),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    browseMode = Color.Unspecified,
    onBrowseMode = Color.White,
)

private val darkSurfaces = VaultistPalette(
    primary = Color.Unspecified,
    primaryVariant = Color.Unspecified,
    secondary = Color(0xFFCCC2DC),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onPrimary = Color.Unspecified,
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
    browseMode = Color.Unspecified,
    onBrowseMode = Color.White,
)

private val rubyLight = lightSurfaces.copy(
    primary = Color(0xFFE0115F),
    primaryVariant = Color(0xFFB80E4C),
    browseMode = Color(0xFFDE3163),
)

private val rubyDark = darkSurfaces.copy(
    primary = Color(0xFFFF6B8F),
    primaryVariant = Color(0xFFFF8FA8),
    onPrimary = Color(0xFF3B0015),
    browseMode = Color(0xFFDE3163),
)

private val forestLight = lightSurfaces.copy(
    primary = Color(0xFF2E5A3C),
    primaryVariant = Color(0xFF1F3F2A),
    browseMode = Color(0xFF3D7A52),
)

private val forestDark = darkSurfaces.copy(
    primary = Color(0xFF4A8B5E),
    primaryVariant = Color(0xFF3D7A52),
    onPrimary = Color.White,
    browseMode = Color(0xFF4A8B5E),
)

fun paletteFor(theme: AppColorTheme, dark: Boolean): VaultistPalette = when (theme) {
    AppColorTheme.Ruby -> if (dark) rubyDark else rubyLight
    AppColorTheme.Forest -> if (dark) forestDark else forestLight
}
