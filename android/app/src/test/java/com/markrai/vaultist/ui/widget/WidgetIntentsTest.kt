package com.markrai.vaultist.ui.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetIntentsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun openNoteIntentCarriesCanonicalId() {
        val intent = WidgetIntents.openNote(context, "Folder/Note", appWidgetId = 3)
        assertEquals("Folder/Note", intent.getStringExtra(WidgetIntents.EXTRA_NOTE_ID))
    }

    @Test
    fun differentWidgetIdsProduceDifferentDataUris() {
        val first = WidgetIntents.openNote(context, "same", appWidgetId = 1)
        val second = WidgetIntents.openNote(context, "same", appWidgetId = 2)
        assertNotEquals(first.data, second.data)
    }

    @Test
    fun extractNoteIdReturnsValueOnce() {
        val intent = WidgetIntents.openNote(context, "Folder/Note", 1)
        assertEquals("Folder/Note", WidgetIntents.extractNoteId(intent))
    }

    @Test
    fun openNoteFromWidgetConsumesOnce() {
        val bridge = OpenNoteFromWidget()
        bridge.offer("a")
        assertEquals("a", bridge.consume())
        assertNull(bridge.consume())
    }
}
