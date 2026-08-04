package com.markrai.vaultist.testutil

object ApiFixtures {
    const val INDEX_STATE = """{
      "state":"ready","generation":1,"noteCount":4,"assetCount":1,"errorCount":0
    }"""

    const val STATUS = """{
      "service":"vaultist","version":"v1","status":"ok",
      "index":{"state":"ready","generation":1,"noteCount":4,"assetCount":1,"errorCount":0}
    }"""

    const val VAULT = """{
      "name":"Contract Vault","noteCount":4,"assetCount":1,"generation":1,
      "indexedAt":"2026-01-01T00:00:00Z","readOnly":true
    }"""

    const val BROWSE_ROOT = """{
      "items":[
        {"kind":"folder","name":"Folder","path":"Folder"},
        {"kind":"note","id":"Home","name":"Home.md","title":"Home","path":"Home.md"}
      ],
      "nextCursor":null,"folder":""
    }"""

    const val SEARCH = """{
      "items":[
        {"kind":"note","id":"Folder/Other","name":"Other.md","title":"Other","path":"Folder/Other.md"}
      ],
      "nextCursor":null,"query":"other"
    }"""

    const val NOTE = """{
      "id":"Folder/Note","path":"Folder/Note.md","filename":"Note.md","title":"Note",
      "aliases":[],"headings":[],"links":[],"attachments":[],
      "modifiedAt":"2026-01-01T00:00:00Z","size":64,
      "revision":"sha256:abc","content":"# Note","error":""
    }"""

    const val BACKLINKS = """{
      "noteId":"Home",
      "items":[{
        "sourceId":"Folder/Note","sourceTitle":"Note","sourcePath":"Folder/Note.md",
        "line":2,"column":1,"context":"[[Home]]","occurrenceKind":"wiki"
      }]
    }"""

    fun noteNotFoundError() = """{"error":{"code":"note_not_found","message":"Note was not found"}}"""
}
