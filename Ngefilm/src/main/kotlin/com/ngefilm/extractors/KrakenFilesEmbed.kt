package com.ngefilm.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.ExtractorLinkType

class KrakenFilesEmbed : ExtractorApi() {
    override val name = "KrakenFiles"
    override val mainUrl = "https://krakenfiles.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val pageUrl = httpsify(url)
        val embedUrl = when {
            pageUrl.contains("/embed-video/") -> pageUrl
            pageUrl.contains("/view/") -> pageUrl.replace("/view/", "/embed-video/")
            pageUrl.contains("/download/") -> pageUrl.replace("/download/", "/embed-video/")
            else -> pageUrl
        }

        val html = app.get(embedUrl, referer = referer ?: mainUrl).text
        val body = html.replace("\\/", "/")

        // Find m3u8 first
        val m3u8 = listOf(
            """"file"\s*:\s*"(https?://[^"]+\.m3u8)"""",
            """source\s+src=['"](https?://[^'"\\]+\.m3u8)['"]""",
            """['"](https?://[^'"\\]+\.m3u8)['"]""",
        ).firstNotNullOfOrNull { pat ->
            Regex(pat, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
        }
        if (m3u8 != null) {
            M3u8Helper.generateM3u8(name, m3u8, embedUrl).forEach(callback)
            return
        }

        // Fallback to MP4
        val mp4 = listOf(
            """source\s+src=['"](https?://[^'"\\]+\.mp4)['"]""",
            """"file"\s*:\s*"(https?://[^"]+\.mp4)"""",
        ).firstNotNullOfOrNull { pat ->
            Regex(pat, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
        }

        if (mp4 != null) {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = mp4,
                    referer = embedUrl,
                    quality = Qualities.P1080.value,
                    type = ExtractorLinkType.VIDEO,
                )
            )
        }
    }
}
