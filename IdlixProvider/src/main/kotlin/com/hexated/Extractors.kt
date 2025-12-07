package com.hexated

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.fasterxml.jackson.annotation.JsonProperty

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

        val pageRef = referer ?: "$mainUrl/"
        val document = app.get(url, referer = pageRef).document

        val hash = url.substringAfter("data=").substringBefore("&")

        val response = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf(
                "hash" to hash,
                "r" to pageRef
            ),
            referer = pageRef,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsed<ResponseSource>()

        // pakai securedLink kalau ada, fallback ke videoSource
        val realSource = response.securedLink?.ifBlank { null } ?: response.videoSource

        // referer untuk HLS (M3u8Helper akan set referer di dalam request)
        val streamReferer = mainUrl

        // generate semua stream kualitas
        M3u8Helper.generateM3u8(
            name,
            realSource,
            streamReferer
        ).forEach { stream ->

            callback.invoke(
                newExtractorLink(
                    name = "$name ${stream.quality}p",
                    source = name,
                    url = stream.url,
                    type = ExtractorLinkType.M3U8
                )
            )
        }

        // ========== SUBTITLE ================
        document.select("script").forEach { script ->
            val data = script.data()
            if (data.contains("eval(function")) {

                val unpack = getAndUnpack(data)

                val subJson = unpack
                    .substringAfter("\"tracks\":[")
                    .substringBefore("],")

                tryParseJson<List<Tracks>>("[$subJson]")?.forEach { sub ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            getLanguage(sub.label ?: ""),
                            sub.file
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


    // Response JSON dari jeniusplay/player/index.php
    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean?,
        @JsonProperty("videoSource") val videoSource: String,
        @JsonProperty("securedLink") val securedLink: String?
    )

    // Subtitle JSON
    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?
    )
}