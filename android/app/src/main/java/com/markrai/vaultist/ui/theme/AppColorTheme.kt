package com.markrai.vaultist.ui.theme

enum class AppColorTheme(val id: String) {
    Ruby("ruby"),
    Forest("forest"),
    ;

    companion object {
        fun fromId(id: String?): AppColorTheme =
            entries.firstOrNull { it.id == id } ?: Ruby
    }
}
