package com.markrai.vaultist.ui.theme

import androidx.compose.ui.graphics.Color

enum class HeadingColorPalette(val id: String) {
    Classic("classic"),
    ClassicReversed("classic_reversed"),
    Teal("teal"),
    TealReversed("teal_reversed"),
    ;

    fun colorForLevel(level: Int): Color {
        val clamped = level.coerceIn(1, 6)
        val sourceLevel = when (this) {
            Classic, Teal -> clamped
            ClassicReversed, TealReversed -> 7 - clamped
        }
        return when (this) {
            Classic, ClassicReversed -> classicColor(sourceLevel)
            Teal, TealReversed -> tealColor(sourceLevel)
        }
    }

    fun swatchColors(): List<Color> = (1..6).map { colorForLevel(it) }

    companion object {
        fun fromId(id: String?): HeadingColorPalette =
            entries.firstOrNull { it.id == id } ?: Classic

        private fun classicColor(level: Int): Color = when (level) {
            1 -> Color(0xFFE57373) // Red
            2 -> Color(0xFFFFB74D) // Orange
            3 -> Color(0xFFFFD54F) // Yellow
            4 -> Color(0xFF81C784) // Green
            5 -> Color(0xFF64B5F6) // Blue
            else -> Color(0xFFBA68C8) // Purple
        }

        private fun tealColor(level: Int): Color = when (level) {
            1 -> Color(0xFFE53935) // Red
            2 -> Color(0xFFFB8C00) // Orange
            3 -> Color(0xFFFFC107) // Gold
            4 -> Color(0xFF26A69A) // Teal
            5 -> Color(0xFF1E88E5) // Blue
            else -> Color(0xFF8E24AA) // Purple
        }
    }
}
