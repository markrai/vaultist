package com.markrai.vaultist.ui.markdown

import com.caverock.androidsvg.SVG

/**
 * Process-wide AndroidSVG policy. Configure this before Coil can decode vault SVGs.
 */
object SvgParserSecurity {
    fun configure() {
        SVG.setInternalEntitiesEnabled(false)
        SVG.deregisterExternalFileResolver()
    }
}
