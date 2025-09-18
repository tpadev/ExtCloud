package com.dutamovie

import com.lagradost.cloudstream3.extractors.JWPlayer

class EmbedPyrox : JWPlayer() {
    override var name = "embedpyrox"
    override var mainUrl = "https://embedpyrox.xyz"
}

class Helvid : JWPlayer() {
    override var name = "helvid"
    override var mainUrl = "https://helvid.net"
}

class P2PPlay : JWPlayer() {
    override var name = "p2pplay"
    override var mainUrl = "https://pm21.p2pplay.pro"
}