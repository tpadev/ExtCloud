package com.midasxxi

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

class Playcinematic : ExtractorApi() {
    override var name = "Playcinematic"
    override var mainUrl = "https://playcinematic.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val hash = url.substringAfter("data=")

        val response = app.post(
            url = "$mainUrl/player/index.php?data=$hash&do=getVideo",
            data = mapOf(
                "hash" to hash,
                "r" to (referer ?: "")
            ),
            referer = referer,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsed<VideoResponse>()

        response.videoSource?.let { m3u8 ->
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8,
                    type = ExtractorLinkType.M3U8
                )
            )
        }

        document.select("script").forEach { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val unpacked = getAndUnpack(script.data())
                val tracksJson = unpacked
                    .substringAfter("\"tracks\":[")
                    .substringBefore("]")

                tryParseJson<List<SubtitleTrack>>("[$tracksJson]")?.forEach { sub ->
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = getLanguage(sub.label),
                            url = sub.file
                        )
                    )
                }
            }
        }
    }

    private fun getLanguage(label: String?): String {
        if (label == null) return "Unknown"
        return when {
            label.contains("indo", true) ||
            label.contains("indonesia", true) ||
            label.contains("bahasa", true) -> "Indonesian"
            else -> label
        }
    }
}

data class VideoResponse(
    @JsonProperty("videoSource")
    val videoSource: String?
)

data class SubtitleTrack(
    @JsonProperty("label")
    val label: String?,
    @JsonProperty("file")
    val file: String
)
