package com.ngefilm

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.ngefilm.extractors.Chillx
import com.ngefilm.extractors.KrakenFilesEmbed

@CloudstreamPlugin
class NgefilmProvider : BasePlugin() {
    override fun load() {
        registerMainAPI(Ngefilm())
        registerExtractorAPI(Chillx())
        registerExtractorAPI(KrakenFilesEmbed())
    }
}
