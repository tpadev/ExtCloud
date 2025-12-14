package com.dutamovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DutaMoviePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DutaMovie())
        registerMainAPI(PusatMovie())
        registerExtractorAPI(Dingtezuni())
        registerExtractorAPI(Bingezove())
        registerExtractorAPI(Mivalyo())
        registerExtractorAPI(Hglink())
        registerExtractorAPI(Ryderjet())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(Pm21p2p())
        registerExtractorAPI(Movearnpre())
        registerExtractorAPI(Streamcasthub())
        registerExtractorAPI(Dm21upns())
        registerExtractorAPI(Dm21())
        registerExtractorAPI(Dintezuvio())
    }
}
