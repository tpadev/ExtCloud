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

    private val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val pageRef = "$mainUrl/"

        val document = app.get(
            url,
            referer = pageRef,
            headers = mapOf("User-Agent" to UA)
        ).document

        val videoId = when {
            url.contains("/video/") -> url.substringAfterLast("/")
            url.contains("data=") -> url.substringAfter("data=").substringBefore("&")
            else -> return
        }

        val response = app.post(
            url = "$mainUrl/player/index.php?do=getVideo&data=$videoId",
            data = mapOf("hash" to videoId, "r" to pageRef),
            referer = pageRef,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to mainUrl,
                "User-Agent" to UA
            )
        ).parsed<ResponseSource>()

        val masterM3u8 = response.securedLink ?: return

        val headers = mapOf(
            "Origin" to mainUrl,
            "Referer" to pageRef,
            "User-Agent" to UA,
            "Accept" to "*/*"
        )

        // ===== AUTO SOURCE =====
        callback.invoke(
            newExtractorLink(
                name = "Jenius AUTO",
                source = name,
                url = masterM3u8,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = pageRef
                this.headers = headers
            }
        )

        // ===== PARSE VARIANT → JENIUS 720 / 480 =====
        try {
            val text = app.get(masterM3u8, headers = headers).text
            val lines = text.lines()

            var height: Int? = null
            val added = mutableSetOf<Int>()

            for (line in lines) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    height = Regex("RESOLUTION=\\d+x(\\d+)")
                        .find(line)?.groupValues?.get(1)?.toIntOrNull()
                } else if (
                    height != null &&
                    !line.startsWith("#") &&
                    line.isNotBlank() &&
                    height in listOf(720, 480)
                ) {
                    if (added.add(height!!)) {
                        val variantUrl =
                            if (line.startsWith("http")) line
                            else masterM3u8.substringBeforeLast("/") + "/" + line

                        callback.invoke(
                            newExtractorLink(
                                name = "Jenius ${height}p",
                                source = name,
                                url = variantUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = pageRef
                                this.headers = headers
                            }
                        )
                    }
                    height = null
                }
            }
        } catch (_: Throwable) {
        }

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