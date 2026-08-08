package com.markrai.vaultist.ui.widget

import androidx.glance.unit.ColorProvider
import com.markrai.vaultist.ui.theme.AppColorTheme
import com.markrai.vaultist.ui.theme.paletteFor

internal fun widgetPrimaryColor(theme: AppColorTheme, darkTheme: Boolean): ColorProvider =
    ColorProvider(paletteFor(theme, darkTheme).primary)

internal fun widgetOnPrimaryColor(theme: AppColorTheme, darkTheme: Boolean): ColorProvider =
    ColorProvider(paletteFor(theme, darkTheme).onPrimary)
