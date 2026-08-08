package com.markrai.vaultist.ui.markdown

import android.graphics.Bitmap
import android.graphics.Picture
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGExternalFileResolver
import com.markrai.vaultist.VaultistApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = VaultistApp::class)
class SvgRenderingSecurityTest {
    @Test(timeout = 5_000)
    fun scriptAndExternalImagePayloadRendersAsStaticPicture() {
        val payload =
            """
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">
              <script>throw new Error("must not execute")</script>
              <image href="https://example.invalid/tracker.png" width="10" height="10"/>
              <rect width="10" height="10" fill="red"/>
            </svg>
            """.trimIndent()
        val svg = SVG.getFromString(payload)

        val picture: Picture = svg.renderToPicture()
        assertNotNull(picture)
        assertEquals(10, picture.width)
        assertEquals(10, picture.height)
    }

    @Test(timeout = 5_000)
    fun productionPolicyDisablesEntitiesBeforeNestedPayloadIsParsed() {
        assertFalse("internal XML entities must be disabled", SVG.isInternalEntitiesEnabled())
        val payload = requireNotNull(
            javaClass.classLoader?.getResource("security/nested-internal-entities.svg"),
        ).readText()

        val picture = SVG.getFromString(payload).renderToPicture()
        assertEquals(10, picture.width)
        assertEquals(10, picture.height)
        assertFalse("internal XML entities must remain disabled", SVG.isInternalEntitiesEnabled())
    }

    @Test(timeout = 5_000)
    fun productionPolicyRemovesExternalFileResolver() {
        var externalResolutionAttempted = false
        SVG.registerExternalFileResolver(
            object : SVGExternalFileResolver() {
                override fun resolveImage(filename: String?): Bitmap? {
                    externalResolutionAttempted = true
                    return null
                }
            },
        )

        SvgParserSecurity.configure()
        val svg = SVG.getFromString(
            """
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">
              <image href="https://example.invalid/tracker.png" width="10" height="10"/>
            </svg>
            """.trimIndent(),
        )
        svg.renderToPicture()

        assertFalse("external SVG resources must remain disabled", externalResolutionAttempted)
    }
}
