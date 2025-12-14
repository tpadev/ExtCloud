package com.dutamovie

import com.lagradost.cloudstream3.*

class PusatMovie : DutaMovie() {

    override var mainUrl = "https://marcuspryor.com"
    private var directUrl: String? = null
    override var name = "PusatMovie🎉"
    override val hasMainPage = true
    override var lang = "id"

}
