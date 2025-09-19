package com.dramaid.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class GdriveWebId : ExtractorApi() {
    override val name = "GdriveWebId"
    override val mainUrl = "https://gdrive.web.id"
    override val requiresReferer = false

    data class ApiResponse(
        @JsonProperty("sources") val sources: List<Source>?,
        @JsonProperty("tracks") val tracks: List<Track>?
    )

    data class Source(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?,
        @JsonProperty("type") val type: String?
    )

    data class Track(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // ambil ID dari embed url, amanin biar gak kena error val
        val id = url.substringAfterLast("/").substringBefore("?")

        val payload = mapOf(
            "query" to mapOf(
                "source" to "db",
                "id" to id,
                "download" to ""
            )
        )

        val json = app.post("https://miku.gdrive.web.id/api/", json = payload)
            .parsedSafe<ApiResponse>() ?: return

        json.sources?.forEach { src ->
            val videoUrl = src.file ?: return@forEach
            callback(
                newExtractorLink(
                    name,
                    src.label ?: "GDrive",
                    videoUrl
                ) {
                    this.referer = url
                    this.quality = getQualityFromName(src.label ?: "")
                    this.isM3u8 = videoUrl.endsWith(".m3u8")
                }
            )
        }

        json.tracks?.forEach { tr ->
            val subUrl = tr.file ?: return@forEach
            subtitleCallback(
                SubtitleFile(tr.label ?: "Subtitle", subUrl)
            )
        }
    }
}