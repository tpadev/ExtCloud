package com.dutamovie

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

class EmbedProx : ExtractorApi() {
    override val name = "EmbedProx"
    override val mainUrl = "https://embedprox.xyz"
    override val requiresReferer = false  // ✅ Embedprox tidak butuh referer

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink> {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Accept" to "*/*"
        )

        // ambil isi master.txt
        val body = app.get(url, headers = headers).text

        // regex untuk ambil stream info (resolution + link)
        val regex = Regex("#EXT-X-STREAM-INF.*RESOLUTION=(\\d+)x(\\d+).*\\n(https?://.*\\.m3u8)")

        return regex.findAll(body).map { match ->
            val height = match.groupValues[2].toIntOrNull() ?: 0
            val quality = when {
                height >= 1080 -> Qualities.P1080.value
                height >= 720 -> Qualities.P720.value
                height >= 480 -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }
            val link = match.groupValues[3]

            ExtractorLink(
                name,
                "$name ${height}p",
                link,
                mainUrl,
                quality,
                isM3u8 = true
            )
        }.toList()
    }
}

open class Dingtezuni : ExtractorApi() {
    override val name = "Earnvids"
    override val mainUrl = "https://dingtezuni.com"
    override val requiresReferer = true

 override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
	        "User-Agent" to USER_AGENT,
        )
        
        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if(result.contains("var links")){
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        // m3u8 urls could be prefixed by 'file:', 'hls2:' or 'hls4:', so we just match ':'
        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            generateM3u8(
                name,
                fixUrl(m3u8Match.groupValues[1]),
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
        }
    }

    private fun getEmbedUrl(url: String): String {
		return when {
			url.contains("/d/") -> url.replace("/d/", "/v/")
			url.contains("/download/") -> url.replace("/download/", "/v/")
			url.contains("/file/") -> url.replace("/file/", "/v/")
			else -> url.replace("/f/", "/v/")
		}
	}

}
