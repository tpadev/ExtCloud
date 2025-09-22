package com.ngefilm

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.ngefilm.extractors.Bangjago
import com.ngefilm.extractors.BingeZove
import com.ngefilm.extractors.Hglink
import com.ngefilm.extractors.PlayerNgefilm21

@CloudstreamPlugin
class NgefilmProvider : BasePlugin() {
    override fun load() {
        // Main provider
        registerMainAPI(Ngefilm())

        // Custom extractors (host yang belum bawaan CS3)
        registerExtractorAPI(PlayerNgefilm21())
        registerExtractorAPI(Bangjago())
        registerExtractorAPI(Hglink())
        registerExtractorAPI(BingeZove())

        // NOTE: Host populer (termasuk KrakenFiles, Streamtape, Dood, dst)
        // sudah ada extractor bawaan Cloudstream — tidak perlu daftar lagi.
    }
}
