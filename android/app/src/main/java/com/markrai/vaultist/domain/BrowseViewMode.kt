package com.markrai.vaultist.domain

enum class BrowseViewMode(val id: String) {
    Stacked("stacked"),
    Grid("grid"),
    ;

    companion object {
        fun fromId(id: String?): BrowseViewMode =
            entries.firstOrNull { it.id == id } ?: Stacked
    }
}
