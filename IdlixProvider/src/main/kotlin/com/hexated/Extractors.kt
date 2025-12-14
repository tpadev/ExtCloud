package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

class IdlixPlayer : ExtractorApi() {

    override val name = "Jeniusplay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 🔑 REFERER HARUS ROOT
        val pageRef = "$mainUrl/"

        // Load embed page (buat subtitle & cookie awal)
        val document = app.get(
            url = url,
            referer = pageRef,
            headers = mapOf(
                "User-Agent" to UA
            )
        ).document

        // Ambil video id (aman untuk /video/xxxx atau data=xxxx)
        val videoId = when {
            url.contains("/video/") -> url.substringAfterLast("/")
            url.contains("data=") -> url.substringAfter("data=").substringBefore("&")
            else -> return
        }

        // 🔑 REQUEST TOKEN BARU
        val response = app.post(
            url = "$mainUrl/player/index.php?do=getVideo&data=$videoId",
            data = mapOf(
                "hash" to videoId,
                "r" to pageRef
            ),
            referer = pageRef,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to mainUrl,
                "User-Agent" to UA,
                "Accept" to "*/*"
            )
        ).parsed<ResponseSource>()

        // ❗ WAJIB securedLink
        val m3u8Url = response.securedLink ?: return

        // 🎯 KIRIM KE PLAYER DENGAN HEADER BROWSER
        callback.invoke(
            newExtractorLink(
                name,
                name,
                m3u8Url,
                ExtractorLinkType.M3U8
            ) {
                this.referer = pageRef
                this.headers = mapOf(
                    "Origin" to mainUrl,
                    "Referer" to pageRef,
                    "User-Agent" to UA,
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )
            }
        )

        // ===== SUBTITLE =====
        document.select("script").forEach { script ->
            if (script.data().contains("eval(function")) {
                val unpack = getAndUnpack(script.data())
                val subData = unpack
                    .substringAfter("\"tracks\":[")
                    .substringBefore("],")

                tryParseJson<List<Tracks>>("[$subData]")?.forEach { sub ->
                    subtitleCallback.invoke(
                        SubtitleFile(
                            getLanguage(sub.label ?: ""),
                            sub.file
                        )
                    )
                }
            }
        }
    }

    private fun getLanguage(str: String): String =
        if (str.contains("indonesia", true) || str.contains("bahasa", true))
            "Indonesian" else str

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean?,
        @JsonProperty("videoSource") val videoSource: String?,
        @JsonProperty("securedLink") val securedLink: String?
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?
    )
}