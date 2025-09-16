package com.dutamovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.dutamovie.extractors.EmbedPyrox
import com.dutamovie.extractors.P2pPlay
import com.dutamovie.extractors.StreamCastHub

@CloudstreamPlugin
class DutaMoviePlugin : Plugin() {
    override fun load(context: Context) {
        // Register provider
        registerMainAPI(DutaMovie())

        // Register extractor khusus
        registerExtractorAPI(EmbedPyrox())
        registerExtractorAPI(P2pPlay())
        registerExtractorAPI(StreamCastHub())
    }
}
