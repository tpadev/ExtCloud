package com.dutamovie

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor

class PusatMovie : DutaMovie() {
    override var mainUrl = "https://carteretliteracy.org"
    override var name = "PusatMovie🎉"
}
