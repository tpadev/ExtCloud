package com.dramaid.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName

class GdriveWebId : ExtractorApi() {
    override val name = "GdriveWebId"
    override val mainUrl = "https://gdrive.web.id"
    override val requiresReferer = false

    // ==== MODEL RESPON API ====
    data class ApiResponse(
        @JsonProperty("sources") val sources: List<Source>?,
        @JsonProperty("tracks") val tracks: List<Track>?
    )

    data class Source(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?,
        @JsonProperty("type") val type: String? = null
    )

    data class Track(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?
    )

    private fun extractId(iframeUrl: String): String {
        // Support kedua pola: /embed/ID dan /embed/?ID
        val after = iframeUrl.substringAfter("/embed/", iframeUrl)
        val id0 = if (after.startsWith("?")) after.substring(1) else after
        return id0.substringBefore("&").substringBefore("#").substringBefore("?")
    }

    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = extractId(url)

        // Coba primary lalu fallback
        val endpoints = listOf(
            "https://miku.gdrive.web.id/api/",
            "https://backup.gdrive.web.id/api/"
        )

        var parsed: ApiResponse? = null

        for (api in endpoints) {
            // 1) Coba body JSON (format sama seperti DevTools)
            try {
                val payload = mapOf(
                    "query" to mapOf(
                        "source" to "db",
                        "id" to id,
                        "download" to ""
                    )
                )
                parsed = app.post(api, json = payload).parsedSafe<ApiResponse>()
                if (parsed?.sources?.isNotEmpty() == true) break
            } catch (_: Throwable) { /* lanjut fallback */ }

            // 2) Fallback: form-encoded sederhana (beberapa setup menerima ini)
            try {
                parsed = app.post(api, data = mapOf("id" to id)).parsedSafe<ApiResponse>()
                if (parsed?.sources?.isNotEmpty() == true) break
            } catch (_: Throwable) { /* coba endpoint berikutnya */ }
        }

        val resp = parsed ?: return

        // Kirim link video
        resp.sources?.forEach { s ->
            val videoUrl = s.file ?: return@forEach
            callback(
                ExtractorLink(
                    source = name,                                 // nama extractor
                    name = s.label ?: "GDrive",                    // label kualitas
                    url = videoUrl,                                // direct link (videoplayback/mp4/m3u8)
                    referer = url,                                 // referer = halaman embed
                    quality = getQualityFromName(s.label ?: ""),   // mapping 360p/480p/720p dst
                    isM3u8 = videoUrl.endsWith(".m3u8")
                )
            )
        }

        // Kirim subtitle
        resp.tracks?.forEach { t ->
            val subUrl = t.file ?: return@forEach
            subtitleCallback(SubtitleFile(t.label ?: "Subtitle", subUrl))
        }
    }
}