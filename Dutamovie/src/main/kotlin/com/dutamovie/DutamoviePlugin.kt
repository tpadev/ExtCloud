package com.dutamovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.dutamovie.extractors.EmbedPyrox   // ⬅️ tambahkan import extractor

@CloudstreamPlugin
class DutaMoviePlugin : Plugin() {
    override fun load(context: Context) {
        // Register provider
        registerMainAPI(DutaMovie())

        // Register extractor khusus
        registerExtractorAPI(EmbedPyrox())
    }
}

