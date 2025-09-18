package com.dramaid.extractors

import com.lagradost.cloudstream3.extractors.XStreamCdn

class Vanfem : XStreamCdn() {
    override val name: String = "Vanfem"
    override val mainUrl: String = "https://vanfem.com"
}

