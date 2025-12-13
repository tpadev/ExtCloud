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

open class IdlixPlayer : ExtractorApi() {

    override val name = "Jeniusplay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 🔑 referer yang BENAR
        val pageRef = "$mainUrl/"

        val document = app.get(url, referer = pageRef).document

        val hash = url.substringAfter("data=").substringBefore("&")

        val response = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf(
                "hash" to hash,
                "r" to pageRef
            ),
            referer = pageRef,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).parsed<ResponseSource>()

        // ✅ PAKAI securedLink DULU
        val m3u8Url = response.securedLink?.takeIf { it.isNotBlank() }
            ?: response.videoSource

        // ✅ KIRIM 1 LINK AUTO SAJA (PALING STABIL)
        callback.invoke(
            newExtractorLink(
                name,
                name,
                m3u8Url,
                ExtractorLinkType.M3U8
            ) {
                
                this.referer = pageRef
            }
        )

        document.select("script").forEach { script ->
            if (script.data().contains("eval(function")) {
                val subData = getAndUnpack(script.data())
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
        if (
            str.contains("indonesia", true) ||
            str.contains("bahasa", true)
        ) "Indonesian" else str

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean,
        @JsonProperty("videoSource") val videoSource: String,
        @JsonProperty("securedLink") val securedLink: String?
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?
    )
}