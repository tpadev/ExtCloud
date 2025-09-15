package com.dutamovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DutaMoviePlugin : Plugin() {
    override fun load(context: Context) {
        // Register only the provider. All host handling is done inside DutaMovie.loadLinks.
        registerMainAPI(DutaMovie())
    }
}
