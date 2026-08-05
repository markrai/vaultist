package com.markrai.vaultist.domain

enum class ModifiedDateStyle(val id: String) {
    Absolute("absolute"),
    Relative("relative"),
    ;

    companion object {
        fun fromId(id: String?): ModifiedDateStyle =
            entries.firstOrNull { it.id == id } ?: Absolute
    }
}
