package com.dutamovie.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.*

class EmbedPyrox : ExtractorApi() {
    override val name = "EmbedPyrox"
    override val mainUrl = "https://embedpyrox.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Ambil token dari url iframe (beberapa variasi path)
        val token = when {
            url.contains("data=") -> url.substringAfter("data=").substringBefore("&")
            url.contains("/video/") -> url.substringAfter("/video/").substringBefore("?")
            url.contains("/v/") -> url.substringAfter("/v/").substringBefore("?")
            else -> url.substringAfterLast("/").substringBefore("?")
        }
        val apiUrl = "$mainUrl/player/index.php?data=$token&do=getVideo"

        // Kirim POST ke API FireHLS
        val res = app.post(
            apiUrl,
            referer = url,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "Origin" to mainUrl
            )
        ).text

        // Cari link m3u8 dari response
        val m3u8 = Regex("https?://[^\"]+\\.m3u8").find(res)?.value
        if (m3u8 != null) {
            M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
            return
        }

        // Fallback: cari link m3u8 yang ter-encode (percent-encoding)
        runCatching {
            val enc = Regex("https%3A%2F%2F[^\"']+").find(res)?.value
            val decoded = if (enc != null) java.net.URLDecoder.decode(enc, "UTF-8") else null
            if (decoded != null && decoded.contains(".m3u8")) {
                M3u8Helper.generateM3u8(name, decoded, mainUrl).forEach(callback)
                return
            }
        }
    }
}
