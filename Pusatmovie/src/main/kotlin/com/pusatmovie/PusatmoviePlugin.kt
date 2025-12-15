package com.pusatmovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PusatmoviePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Pusatmovie())
        registerExtractorAPI(Dingtezuni())
        registerExtractorAPI(Bingezove())
        registerExtractorAPI(Mivalyo())
        registerExtractorAPI(Hglink())
        registerExtractorAPI(Ryderjet())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(Movearnpre())
        registerExtractorAPI(Streamcasthub())
        registerExtractorAPI(Dm21())
        registerExtractorAPI(Dintezuvio())
        registerExtractorAPI(Meplayer())
        registerExtractorAPI(Pm21p2p())
    }
}
