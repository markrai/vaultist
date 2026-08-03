package com.vaultview

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractSchemaTest {
    @Test fun openApiContainsModelsConsumedByAndroid() {
        val candidates = listOf(File("../api/openapi.yaml"), File("../../api/openapi.yaml"))
        val contract = candidates.firstOrNull(File::isFile)?.readText() ?: error("api/openapi.yaml not found")
        listOf("/notes/{id}:", "NoteResponse:", "LinkOccurrence:", "Backlink:", "IndexState:", "ErrorResponse:").forEach {
            assertTrue("Missing $it", contract.contains(it))
        }
    }
}
