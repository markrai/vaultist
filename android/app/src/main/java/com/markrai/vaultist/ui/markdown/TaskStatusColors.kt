package com.markrai.vaultist.ui.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TaskNotDoneLight = Color(0xFFC62828)
private val TaskNotDoneDark = Color(0xFFEF5350)
private val TaskDoneLight = Color(0xFF2E7D32)
private val TaskDoneDark = Color(0xFF66BB6A)

/** Fixed light-theme colors for Glance widgets (no theme context at render time). */
fun taskStatusComposeColor(checked: Boolean): Color =
    if (checked) TaskDoneLight else TaskNotDoneLight

@Composable
fun taskStatusColor(checked: Boolean, darkTheme: Boolean): Color = when {
    checked && darkTheme -> TaskDoneDark
    checked -> TaskDoneLight
    darkTheme -> TaskNotDoneDark
    else -> TaskNotDoneLight
}
