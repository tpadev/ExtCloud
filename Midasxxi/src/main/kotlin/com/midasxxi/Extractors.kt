package com.midasxxi

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

class Playcinematic : ExtractorApi() {

    override var name = "Playcinematic"
    override var mainUrl = "https://playcinematic.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
       
        val res = app.get(
            url = url,
            referer = referer,
            allowRedirects = false
        )

        
        val videoUrl = res.headers["location"] ?: return

        callback.invoke(
            newExtractorLink(
                name,
                name,
                videoUrl,
                ExtractorLinkType.VIDEO
            )
        )
    }
}
