package com.markrai.vaultist.domain

enum class BrowseSortMode(val id: String) {
    Alphabetical("alphabetical"),
    ModifiedDesc("modified_desc"),
    ;

    companion object {
        fun fromId(id: String?): BrowseSortMode =
            entries.firstOrNull { it.id == id } ?: Alphabetical
    }
}
