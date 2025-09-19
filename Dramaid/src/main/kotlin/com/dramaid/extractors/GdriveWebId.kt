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

    /** Ambil ID dari berbagai pola:
     *  - https://gdrive.web.id/embed/IzRHSEn8
     *  - https://gdrive.web.id/embed/?IzRHSEn8
     *  - ...?id=IzRHSEn8 atau ...?hash=IzRHSEn8
     */
    private fun extractId(iframeUrl: String): String? {
        val rxPath = Regex("/embed/([^?&#/]+)")
        val rxQuery = Regex("[?&](?:id|hash)=([^&]+)")
        val p1 = rxPath.find(iframeUrl)?.groupValues?.getOrNull(1)
        val p2 = rxQuery.find(iframeUrl)?.groupValues?.getOrNull(1)
        val candidate = p1 ?: p2 ?: iframeUrl.substringAfter("/embed/", "")
        if (candidate.isEmpty()) return null
        return candidate.substringBefore("&").substringBefore("#").substringBefore("?")
    }

    private fun apiHeaders(embedUrl: String) = mapOf(
        // beberapa setup cek Origin/Referer
        "Origin" to "https://gdrive.web.id",
        "Referer" to embedUrl,
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With" to "XMLHttpRequest"
    )

    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = extractId(url) ?: return

        // Endpoints: primary + fallback
        val endpoints = listOf(
            "https://miku.gdrive.web.id/api/",
            "https://backup.gdrive.web.id/api/"
        )

        // Dua bentuk payload: JSON (sesuai DevTools) lalu fallback form-encoded sederhana
        val payloadJson = mapOf(
            "query" to mapOf(
                "source" to "db",
                "id" to id,
                "download" to ""
            )
        )
        val payloadForm = mapOf("id" to id)

        var resp: ApiResponse? = null
        val headers = apiHeaders(url)

        loop@ for (api in endpoints) {
            // 1) JSON body
            try {
                resp = app.post(api, json = payloadJson, headers = headers).parsedSafe<ApiResponse>()
                if (resp?.sources?.isNotEmpty() == true) break@loop
            } catch (_: Throwable) { /* try next */ }

            // 2) Form body
            try {
                resp = app.post(api, data = payloadForm, headers = headers).parsedSafe<ApiResponse>()
                if (resp?.sources?.isNotEmpty() == true) break@loop
            } catch (_: Throwable) { /* try next */ }
        }

        val jr = resp ?: return

        // Kirim link video ke Cloudstream (Mirror Unduh)
        jr.sources?.forEach { s ->
            val videoUrl = s.file ?: return@forEach
            callback(
                ExtractorLink(
                    source = name,                              // nama extractor
                    name = s.label ?: "GDrive",                 // label (360p/720p/Original)
                    url = videoUrl,                             // videoplayback/mp4/m3u8
                    referer = url,                              // referer = halaman embed
                    quality = getQualityFromName(s.label ?: ""),// parse 360p dst
                    isM3u8 = videoUrl.endsWith(".m3u8")
                )
            )
        }

        // Subtitle (VTT)
        jr.tracks?.forEach { t ->
            val subUrl = t.file ?: return@forEach
            subtitleCallback(SubtitleFile(t.label ?: "Subtitle", subUrl))
        }
    }
}