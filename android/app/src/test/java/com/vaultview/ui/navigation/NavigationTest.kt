package com.vaultview.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationTest {
    @Test fun encodesSlashIdsSpacesAndHeadingFragments() {
        assertEquals("note/Projects%2FVega%20Plan?fragment=First%20Heading", noteRoute("Projects/Vega Plan", "First Heading"))
        assertEquals("backlinks/Projects%2FVega", backlinksRoute("Projects/Vega"))
        assertEquals("image/attachments%2Fmy%20image.png", imageRoute("attachments/my image.png"))
    }
}
