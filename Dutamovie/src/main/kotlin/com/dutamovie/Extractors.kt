package com.dutamovie

import com.lagradost.cloudstream3.extractors.JWPlayer


class Embedpyrox : JWPlayer() {
    override var name = "Embedpyrox"
    override var mainUrl = "https://embedpyrox.xyz"
    override var requiresReferer = false
}

class Helvid : JWPlayer() {
    override var name = "Helvid"
    override var mainUrl = "https://helvid.net"
    override var requiresReferer = false
}

class P2pplay : JWPlayer() {
    override var name = "P2pplay"
    override var mainUrl = "https://pm21.p2pplay.pro"
    override var requiresReferer = false
}
