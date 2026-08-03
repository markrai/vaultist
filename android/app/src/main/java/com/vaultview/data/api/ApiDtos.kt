package com.vaultview.data.api

import com.vaultview.domain.Backlink
import com.vaultview.domain.BrowseItem
import com.vaultview.domain.BrowseKind
import com.vaultview.domain.BrowsePage
import com.vaultview.domain.Heading
import com.vaultview.domain.IndexState
import com.vaultview.domain.LinkCandidate
import com.vaultview.domain.LinkResolution
import com.vaultview.domain.LinkStatus
import com.vaultview.domain.Note
import com.vaultview.domain.NoteLink
import com.vaultview.domain.SearchPage
import com.vaultview.domain.VaultMetadata
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.toIndexState(): IndexState = IndexState(
    state = getString("state"),
    generation = getLong("generation"),
    noteCount = getInt("noteCount"),
    assetCount = getInt("assetCount"),
    errorCount = getInt("errorCount"),
)

internal fun JSONObject.toVaultMetadata(): VaultMetadata = VaultMetadata(
    name = getString("name"),
    noteCount = getInt("noteCount"),
    assetCount = getInt("assetCount"),
    generation = getLong("generation"),
    readOnly = getBoolean("readOnly"),
)

internal fun JSONObject.toBrowsePage(): BrowsePage = BrowsePage(
    items = getJSONArray("items").mapObjects { it.toBrowseItem() },
    nextCursor = optString("nextCursor").takeIf(String::isNotBlank),
    folder = getString("folder"),
)

internal fun JSONObject.toSearchPage(): SearchPage = SearchPage(
    items = getJSONArray("items").mapObjects { it.toBrowseItem() },
    nextCursor = optString("nextCursor").takeIf(String::isNotBlank),
    query = getString("query"),
)

internal fun JSONObject.toBrowseItem(): BrowseItem = BrowseItem(
    kind = if (getString("kind") == "folder") BrowseKind.Folder else BrowseKind.Note,
    id = optString("id").takeIf(String::isNotBlank),
    name = getString("name"),
    title = optString("title").takeIf(String::isNotBlank),
    path = getString("path"),
    error = optString("error").takeIf(String::isNotBlank),
)

internal fun JSONObject.toNote(): Note = Note(
    id = getString("id"),
    path = getString("path"),
    filename = getString("filename"),
    title = getString("title"),
    aliases = arrayOrEmpty("aliases").mapStrings(),
    headings = arrayOrEmpty("headings").mapObjects {
        Heading(level = it.getInt("level"), text = it.getString("text"), slug = it.getString("slug"))
    },
    links = arrayOrEmpty("links").mapObjects { it.toNoteLink() },
    attachments = arrayOrEmpty("attachments").mapStrings(),
    revision = getString("revision"),
    content = getString("content"),
    error = optString("error").takeIf(String::isNotBlank),
)

internal fun JSONObject.toNoteLink(): NoteLink = NoteLink(
    kind = getString("kind"),
    raw = getString("raw"),
    target = getString("target"),
    fragment = optString("fragment").takeIf(String::isNotBlank),
    display = optString("display").takeIf(String::isNotBlank),
    line = getInt("line"),
    column = getInt("column"),
    context = optString("context").takeIf(String::isNotBlank),
    isEmbed = getBoolean("isEmbed"),
    isAsset = getBoolean("isAsset"),
    resolution = getJSONObject("resolution").toResolution(),
)

internal fun JSONObject.toResolution(): LinkResolution = LinkResolution(
    status = when (getString("status")) {
        "resolved" -> LinkStatus.Resolved
        "ambiguous" -> LinkStatus.Ambiguous
        "external" -> LinkStatus.External
        else -> LinkStatus.Missing
    },
    noteId = optString("noteId").takeIf(String::isNotBlank),
    assetId = optString("assetId").takeIf(String::isNotBlank),
    candidates = optJSONArray("candidates")?.mapObjects {
        LinkCandidate(it.getString("id"), it.getString("title"), it.getString("path"))
    }.orEmpty(),
)

internal fun JSONObject.toBacklinks(): List<Backlink> = arrayOrEmpty("items").mapObjects {
    Backlink(
        sourceId = it.getString("sourceId"),
        sourceTitle = it.getString("sourceTitle"),
        sourcePath = it.getString("sourcePath"),
        line = it.getInt("line"),
        column = it.getInt("column"),
        context = it.getString("context"),
        fragment = it.optString("fragment").takeIf(String::isNotBlank),
        display = it.optString("display").takeIf(String::isNotBlank),
    )
}

private fun JSONObject.arrayOrEmpty(name: String): JSONArray =
    optJSONArray(name) ?: JSONArray()

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }

private fun JSONArray.mapStrings(): List<String> = List(length()) { index -> getString(index) }
