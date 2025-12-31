package com.Pencurimovie

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin


@CloudstreamPlugin
class PencurimovieProvider: Plugin() {
    override fun load(context: Context) {
        Pencurimovie.context = context
        registerMainAPI(Pencurimovie())
        registerExtractorAPI(Dsvplay())
        registerExtractorAPI(Hglink())
    }
}
