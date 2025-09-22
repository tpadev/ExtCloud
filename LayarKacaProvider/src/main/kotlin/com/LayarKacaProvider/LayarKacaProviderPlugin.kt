package com.layarKacaProvider

import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.extractors.Filemoon
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin : BasePlugin() {
    override fun load() {
        // Provider utama
        registerMainAPI(LayarKacaProvider())

        // Extractors bawaan
        registerExtractorAPI(EmturbovidExtractor())   // untuk emturbovid.com
        registerExtractorAPI(Filemoon())              // untuk filemoon.sx
        registerExtractorAPI(VidHidePro6())           // untuk vidhide.pro

        // Extractors custom
        registerExtractorAPI(Furher())                // furher.in
        registerExtractorAPI(Furher2())               // 723qrh1p.fun
        registerExtractorAPI(Turbovidhls())           // turbovidhls.com
        registerExtractorAPI(Hownetwork())            // stream.hownetwork.xyz
        registerExtractorAPI(Cloudhownetwork())       // cloud.hownetwork.xyz
    }
}
