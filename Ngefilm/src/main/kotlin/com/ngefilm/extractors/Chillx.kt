package com.ngefilm.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor

class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val pageUrl = httpsify(url)
        val html = app.get(pageUrl, referer = referer ?: mainUrl).text

        // Try to find a gofile link directly in the HTML
        val gofile = Regex("(https?://(?:[\\w-]+\\.)?gofile\\.io/[^'\"<>\\s]+)", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)

        if (gofile != null) {
            loadExtractor(httpsify(gofile), pageUrl, subtitleCallback, callback)
            return
        }

        // Sometimes links are percent-encoded or in atob(...) snippets
        val enc = Regex("https%3A%2F%2F(?:[\\w-]+%2F)*gofile\\.io%2F[^'\"<>\\s]+", RegexOption.IGNORE_CASE)
            .find(html)?.value
        if (enc != null) {
            val decoded = java.net.URLDecoder.decode(enc, "UTF-8")
            loadExtractor(httpsify(decoded), pageUrl, subtitleCallback, callback)
            return
        }

        // atob('...') base64 patterns
        Regex("atob\\(['\"]([A-Za-z0-9+/=]+)['\"]\\)", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            runCatching {
                val b64 = m.groupValues.getOrNull(1) ?: return@forEach
                val decoded = base64Decode(b64)
                val gf = Regex("https?://(?:[\\w-]+\\.)?gofile\\.io/[^'\"<>\\s]+", RegexOption.IGNORE_CASE)
                    .find(decoded)?.value
                if (gf != null) {
                    loadExtractor(httpsify(gf), pageUrl, subtitleCallback, callback)
                    return
                }
            }
        }
    }
}
