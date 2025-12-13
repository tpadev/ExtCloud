package com.oppadrama

import com.lagradost.cloudstream3.mainPageOf

class Dramaid : Oppadrama() {
    override var mainUrl = "https://dramaid.icu"
    override var name = "Dramaid"

    override val mainPage = mainPageOf(
        "series/?status=&type=&order=update" to "Update Terbaru",
    )
}
