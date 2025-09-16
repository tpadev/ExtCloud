package com.dutamovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.dutamovie.extractors.Helvid

@CloudstreamPlugin
class DutaMoviePlugin : Plugin() {
    override fun load(context: Context) {
        // Register provider
        registerMainAPI(DutaMovie())

        // Register only Helvid extractor
        registerExtractorAPI(Helvid())
    }
}
