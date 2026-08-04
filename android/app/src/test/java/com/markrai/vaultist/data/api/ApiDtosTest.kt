package com.markrai.vaultist.data.api

import com.markrai.vaultist.domain.LinkStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiDtosTest {
    @Test
    fun mapsRepresentativeNoteAndStructuredLinks() {
        val note = JSONObject(
            """{
              "id":"Projects/Vega","path":"Projects/Vega.md","filename":"Vega.md","title":"Vega",
              "aliases":["Server"],"headings":[{"level":1,"text":"Vega","slug":"vega"}],
              "links":[
                {"kind":"wiki","raw":"Missing","target":"Missing","line":2,"column":1,"isEmbed":false,"isAsset":false,"resolution":{"status":"missing"}},
                {"kind":"wiki","raw":"Note","target":"Note","line":3,"column":1,"isEmbed":false,"isAsset":false,"resolution":{"status":"ambiguous","candidates":[{"id":"A/Note","title":"Note","path":"A/Note.md"}]}}
              ],
              "attachments":[],"modifiedAt":"2026-01-01T00:00:00Z","size":128,
              "revision":"sha256:abc","content":"# Vega","error":""
            }"""
        ).toNote()
        assertEquals("Projects/Vega", note.id)
        assertEquals(listOf("Server"), note.aliases)
        assertEquals("2026-01-01T00:00:00Z", note.modifiedAt)
        assertEquals(128L, note.size)
        assertEquals(LinkStatus.Missing, note.links[0].resolution.status)
        assertEquals(LinkStatus.Ambiguous, note.links[1].resolution.status)
        assertEquals("A/Note", note.links[1].resolution.candidates.single().id)
        assertNull(note.error)
    }

    @Test
    fun mapsNoteWhenCollectionFieldsAreNull() {
        val note = JSONObject(
            """{
              "id":"Plain","path":"Plain.md","filename":"Plain.md","title":"Plain",
              "aliases":null,"headings":null,"links":null,"attachments":null,
              "modifiedAt":"2026-01-01T00:00:00Z","size":0,
              "revision":"sha256:abc","content":"# Plain","error":""
            }"""
        ).toNote()
        assertEquals("Plain", note.id)
        assertEquals(emptyList<String>(), note.aliases)
        assertEquals(emptyList<com.markrai.vaultist.domain.Heading>(), note.headings)
        assertEquals(emptyList<com.markrai.vaultist.domain.NoteLink>(), note.links)
        assertEquals(emptyList<String>(), note.attachments)
    }

    @Test
    fun mapsBacklinkOccurrenceKind() {
        val backlinks = JSONObject(
            """{
              "noteId":"Home",
              "items":[{
                "sourceId":"Folder/Note","sourceTitle":"Note","sourcePath":"Folder/Note.md",
                "line":2,"column":1,"context":"[[Home]]","occurrenceKind":"wiki"
              }]
            }"""
        ).toBacklinks()
        assertEquals("wiki", backlinks.single().occurrenceKind)
    }

    @Test
    fun mapsBrowseKindsAndCursor() {
        val page = JSONObject("""{"items":[{"kind":"folder","name":"Projects","path":"Projects"}],"nextCursor":"50","folder":""}""").toBrowsePage()
        assertEquals(com.markrai.vaultist.domain.BrowseKind.Folder, page.items.single().kind)
        assertEquals("50", page.nextCursor)
    }
}
