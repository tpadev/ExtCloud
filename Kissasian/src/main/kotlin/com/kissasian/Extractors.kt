package com.kissasian

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.ByseSX


class Strcloud : StreamTape() {
    override var mainUrl = "https://strcloud.in"
}

class Myvidplay : DoodLaExtractor() {
    override var mainUrl = "https://myvidplay.com"
}

class Justplay  : ByseSX() {
    override var name = "Justplay"
    override var mainUrl = "https://justplay.cam"
}