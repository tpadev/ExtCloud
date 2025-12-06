package com.oppadrama

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.extractors.EmturbovidExtractor

@CloudstreamPlugin
class OppadramaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Oppadrama())
        registerExtractorAPI(Smoothpre())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(EmturbovidExtractor())
    }
}
