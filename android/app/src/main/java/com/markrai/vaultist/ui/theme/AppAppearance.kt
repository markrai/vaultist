package com.markrai.vaultist.ui.theme

enum class AppAppearance(val id: String) {
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromId(id: String?): AppAppearance =
            entries.firstOrNull { it.id == id } ?: Light
    }
}
