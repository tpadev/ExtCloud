package com.hexated

import com.hexated.IdlixProvider.ResponseSource
import com.hexated.IdlixProvider.Tracks
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack

class Jeniusplay : ExtractorApi() {

    override var name = "Jeniusplay"
    override var mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val pageReferer = referer ?: "$mainUrl/"

        val document = app.get(url, referer = pageReferer).document

        val hash = url.substringAfter("data=").substringBefore("&")

        val result = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf("hash" to hash, "r" to pageReferer),
            referer = pageReferer,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        )

        val response = result.parsed<ResponseSource>()

        // gunakan securedLink jika ada
        val realSource = response.securedLink?.takeIf { it.isNotBlank() }
            ?: response.videoSource

        val hlsHeaders = mapOf(
            "Referer" to mainUrl,
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
            "X-Requested-With" to "XMLHttpRequest"
        )

        // parse semua kualitas dari m3u8
        M3u8Helper.generateM3u8(
            name,
            realSource,
            pageReferer,
            headers = hlsHeaders
        ).forEach { m3uLink ->
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "${name} ${m3uLink.quality}p",
                    url = m3uLink.url,
                    referer = pageReferer,
                    quality = m3uLink.quality,
                    isM3u8 = true,
                    headers = hlsHeaders,
                    type = ExtractorLinkType.M3U8
                )
            )
        }

        // subtitle
        document.select("script").forEach { script ->
            val data = script.data()
            if (data.contains("eval(function(p,a,c,k,e,d)")) {
                val unpacked = getAndUnpack(data)

                val subData = unpacked
                    .substringAfter("\"tracks\":[")
                    .substringBefore("],")

                AppUtils.tryParseJson<List<Tracks>>("[$subData]")?.forEach { subtitle ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            language = getLanguage(subtitle.label ?: ""),
                            url = subtitle.file
                        )
                    )
                }
            }
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) ||
            str.contains("bahasa", true) -> "Indonesian"

            else -> str
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}