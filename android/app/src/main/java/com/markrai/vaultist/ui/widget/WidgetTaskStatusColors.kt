package com.markrai.vaultist.ui.widget

import androidx.glance.unit.ColorProvider
import com.markrai.vaultist.ui.markdown.taskStatusComposeColor

internal fun widgetTaskStatusTextColor(
    checked: Boolean?,
    colorizeCheckboxStatus: Boolean,
    defaultColor: ColorProvider,
): ColorProvider = when {
    !colorizeCheckboxStatus || checked == null -> defaultColor
    else -> ColorProvider(taskStatusComposeColor(checked))
}
