package com.ngefilm

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.ngefilm.extractors.Chillx

@CloudstreamPlugin
class NgefilmProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Ngefilm())
        registerExtractorAPI(Chillx())
    }
}
