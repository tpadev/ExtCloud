package com.ngefilm

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NgefilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Ngefilm())

        // Custom extractors (host yang belum bawaan CS3)
        registerExtractorAPI(PlayerNgefilm21())
        registerExtractorAPI(Bangjago())
    }
}
