package com.dramaid.extractors

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URI

abstract class FembedLikeExtractor : ExtractorApi() {
    override val requiresReferer = false

    protected open fun resolveBase(url: String): String {
        return runCatching {
            val uri = URI(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return@runCatching mainUrl
            "$scheme://$host"
        }.getOrElse { mainUrl }
    }

    protected open fun extractId(url: String): String? {
        val cleaned = url.substringBefore('?')
        val match = Regex("/(?:v|f|e|d)/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(cleaned)
        val rawId = match?.groupValues?.getOrNull(1)
            ?: cleaned.substringAfterLast('/')
                .takeIf { it.isNotBlank() }
        return rawId?.substringBefore('.')
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = extractId(url) ?: return
        val base = resolveBase(url)
        val host = runCatching { URI(base).host }.getOrNull() ?: base.substringAfter("//", base)
        val payload = mapOf(
            "r" to (referer ?: base),
            "d" to host
        )
        val headers = mapOf(
            "Referer" to base,
            "X-Requested-With" to "XMLHttpRequest"
        )
        val response = runCatching {
            app.post("$base/api/source/$id", data = payload, headers = headers).text
        }.getOrNull() ?: return

        val data = tryParseJson<FembedResponse>(response) ?: return
        val subtitleItems = data.tracks ?: data.captions ?: emptyList()

        data.data.orEmpty().forEach { source ->
            val fileUrl = source.file ?: return@forEach
            val quality = getQualityFromName(source.label)
            val type = when {
                source.type.equals("hls", true) || fileUrl.endsWith(".m3u8", true) -> ExtractorLinkType.M3U8
                else -> ExtractorLinkType.VIDEO
            }
            callback(
                ExtractorLink(
                    source = name,
                    name = source.label ?: name,
                    url = fileUrl,
                    referer = base,
                    quality = quality,
                    type = type,
                    headers = mapOf("Referer" to base)
                )
            )
        }

        subtitleItems.forEach { track ->
            val file = track.file ?: return@forEach
            val label = track.label?.takeIf { it.isNotBlank() } ?: name
            subtitleCallback(SubtitleFile(label, file))
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FembedResponse(
    @JsonProperty("data") val data: List<FembedSource>? = null,
    @JsonProperty("tracks") val tracks: List<FembedCaption>? = null,
    @JsonProperty("captions") val captions: List<FembedCaption>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FembedSource(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null,
    @JsonProperty("type") val type: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FembedCaption(
    @JsonProperty("file") val file: String? = null,
    @JsonProperty("label") val label: String? = null
)