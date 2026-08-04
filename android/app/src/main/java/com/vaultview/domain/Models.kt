package com.vaultview.domain

data class IndexState(
    val state: String,
    val generation: Long,
    val noteCount: Int,
    val assetCount: Int,
    val errorCount: Int,
)

data class VaultMetadata(
    val name: String,
    val noteCount: Int,
    val assetCount: Int,
    val generation: Long,
    val readOnly: Boolean,
)

enum class BrowseKind { Folder, Note }

enum class SearchMode { Files, Content, Ask }

data class BrowseItem(
    val kind: BrowseKind,
    val id: String?,
    val name: String,
    val title: String?,
    val path: String,
    val error: String?,
)

data class BrowsePage(
    val items: List<BrowseItem>,
    val nextCursor: String?,
    val folder: String,
)

data class SearchPage(
    val items: List<BrowseItem>,
    val nextCursor: String?,
    val query: String,
)

data class Heading(val level: Int, val text: String, val slug: String)

data class LinkCandidate(val id: String, val title: String, val path: String)

enum class LinkStatus { Resolved, Missing, Ambiguous, External }

data class LinkResolution(
    val status: LinkStatus,
    val noteId: String?,
    val assetId: String?,
    val candidates: List<LinkCandidate>,
)

data class NoteLink(
    val kind: String,
    val raw: String,
    val target: String,
    val fragment: String?,
    val display: String?,
    val line: Int,
    val column: Int,
    val context: String?,
    val isEmbed: Boolean,
    val isAsset: Boolean,
    val resolution: LinkResolution,
)

data class Note(
    val id: String,
    val path: String,
    val filename: String,
    val title: String,
    val aliases: List<String>,
    val headings: List<Heading>,
    val links: List<NoteLink>,
    val attachments: List<String>,
    val revision: String,
    val content: String,
    val error: String?,
)

data class Backlink(
    val sourceId: String,
    val sourceTitle: String,
    val sourcePath: String,
    val line: Int,
    val column: Int,
    val context: String,
    val fragment: String?,
    val display: String?,
)

sealed interface VaultError {
    data object NotConfigured : VaultError
    data object Unreachable : VaultError
    data object InvalidServerUrl : VaultError
    data class Api(val code: String, val message: String) : VaultError
    data class InvalidResponse(val message: String) : VaultError
}

sealed interface VaultResult<out T> {
    data class Success<T>(val value: T) : VaultResult<T>
    data class Failure(val error: VaultError) : VaultResult<Nothing>
}
