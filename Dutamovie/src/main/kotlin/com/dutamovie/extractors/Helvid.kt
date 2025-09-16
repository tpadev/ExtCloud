package com.dutamovie.extractors

import com.lagradost.cloudstream3.extractors.JWPlayer

class Helvid : JWPlayer() {
    override var name = "Helvid"
    override var mainUrl = "https://helvid.net"
    override val requiresReferer = true
}
