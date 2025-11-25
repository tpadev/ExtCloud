package com.dutamovie

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.extractors.Filesim
import java.net.URI
import com.fasterxml.jackson.annotation.JsonProperty

class FileMoonSx : Filesim() {
    override val mainUrl = "https://filemoon.sx"
    override val name = "FileMoonSx"
}