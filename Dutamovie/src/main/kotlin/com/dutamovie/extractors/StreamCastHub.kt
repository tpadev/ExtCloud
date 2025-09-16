package com.dutamovie.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.app
import java.net.URI
import java.net.URLDecoder

class StreamCastHub : ExtractorApi() {
    override val name = "StreamCastHub"
    override val mainUrl = "https://streamcasthub.store"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val pageUrl = httpsify(url)
        val base = getBaseUrl(pageUrl)

        val rawToken = when {
            pageUrl.contains('#') -> pageUrl.substringAfter('#').substringBefore('?')
            pageUrl.contains("/v/") -> pageUrl.substringAfter("/v/").substringBefore('?')
            else -> pageUrl.substringAfterLast('/').substringBefore('?')
        }

        val endpoints = listOf(
            "$base/player/index.php?data=$rawToken&do=getVideo",
            "$base/index.php?data=$rawToken&do=getVideo",
            "$base/player/?data=$rawToken&do=getVideo",
        )

        for (ep in endpoints) {
            runCatching {
                val res = app.post(
                    ep,
                    referer = pageUrl,
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Origin" to base,
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                    )
                ).text

                sniffM3u8(res)?.let { m3u8 ->
                    M3u8Helper.generateM3u8(name, m3u8, pageUrl).forEach(callback)
                    return
                }
            }
        }

        runCatching {
            val res = app.get(pageUrl, referer = referer ?: base).text
            sniffM3u8(res)?.let { m3u8 ->
                M3u8Helper.generateM3u8(name, m3u8, pageUrl).forEach(callback)
                return
            }
        }
    }

    private fun sniffM3u8(text: String): String? {
        val body = text.replace("\\/", "/")
        val patterns = listOf(
            "\"file\"\\s*:\\s*\"(https?://[^\"]+\\.m3u8)\"",
            "\"src\"\\s*:\\s*\"(https?://[^\"]+\\.m3u8)\"",
            "source\\s+src=['\"](https?://[^'\"]+\\.m3u8)['\"]",
            "['\"](https?://[^'\"]+\\.m3u8)['\"]",
        )
        for (p in patterns) {
            val r = Regex(p, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            r.find(body)?.groupValues?.getOrNull(1)?.let { return it }
        }
        val enc = Regex("https%3A%2F%2F[^'\"]+m3u8").find(body)?.value
        if (enc != null) return URLDecoder.decode(enc, "UTF-8")
        return null
    }

    private fun getBaseUrl(url: String): String {
        val u = URI(url)
        return "${u.scheme}://${u.host}"
    }
}
