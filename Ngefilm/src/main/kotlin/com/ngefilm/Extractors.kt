package com.ngefilm

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile

// -------------------- Bangjago --------------------
class Bangjago : ExtractorApi() {
    override val name = "Bangjago"
    override val mainUrl = "https://bangjago.upns.blog"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val page = app.get(url, referer = referer).text
        val masterUrl = Regex("""https.*?cf-master\.txt.*""")
            .find(page)?.value ?: return

        M3u8Helper.generateM3u8(
            name,
            masterUrl,
            mainUrl
        ).forEach(callback)
    }
}

// -------------------- PlayerNgefilm21 --------------------
class PlayerNgefilm21 : ExtractorApi() {
    override val name = "PlayerNgefilm21"
    override val mainUrl = "https://playerngefilm21.rpmlive.online"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val page = app.get(url, referer = referer).text
        val masterUrl = Regex("""https.*?cf-master\.txt.*""")
            .find(page)?.value ?: return

        M3u8Helper.generateM3u8(
            name,
            masterUrl,
            mainUrl
        ).forEach(callback)
    }
}
