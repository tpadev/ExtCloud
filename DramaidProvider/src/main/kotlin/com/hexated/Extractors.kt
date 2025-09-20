package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName

open class Gcam : ExtractorApi() {
    override val name = "Gcam"
    override val mainUrl = "https://gdrive.cam"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer).text
        val kaken = "kaken\\s*=\\s*\"(.*)\"".toRegex().find(response)?.groupValues?.get(1)
        val json = app.get("https://cdn1.gdrive.cam/api/?${kaken ?: return}=&_=${APIHolder.unixTimeMS}").parsedSafe<Response>()

        json?.sources?.map {
            val quality = getQualityFromName(it.label)
            callback.invoke(
                ExtractorLink(
                    name,
                    "$name ${if(quality != Qualities.Unknown.value) "" else quality}",
                    it.file?: return@map,
                    "",
                    getQualityFromName(it.label)
                )
            )
        }

        json?.tracks?.map {
            subtitleCallback.invoke(
                SubtitleFile(
                    it.label ?: return@map,
                    it.file ?: return@map
                )
            )
        }

    }

    data class Tracks(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class Sources(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class Response(
        @JsonProperty("sources") val sources: ArrayList<Sources>? = arrayListOf(),
        @JsonProperty("tracks") val tracks: ArrayList<Tracks>? = arrayListOf(),
    )

}

class Vanfem : XStreamCdn() {
    override val name: String = "Vanfem"
    override val mainUrl: String = "https://vanfem.com"
}

class Filelions : Filesim() {
    override val name = "Filelions"
    override var mainUrl = "https://filelions.live"
}

open class GdriveWeb : ExtractorApi() {
    override val name = "GdriveWeb"
    override val mainUrl = "https://gdrive.web.id"
    override val requiresReferer = true

    data class GWTracks(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class GWSources(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class GWResponse(
        @JsonProperty("sources") val sources: ArrayList<GWSources>? = arrayListOf(),
        @JsonProperty("tracks") val tracks: ArrayList<GWTracks>? = arrayListOf(),
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)

        // Preferred: API path via `kaken` token
        var kaken = "kaken\\s*=\\s*\"(.*?)\"".toRegex().find(response.text)?.groupValues?.getOrNull(1)
        if (kaken.isNullOrBlank()) {
            kaken = "kaken\\|([A-Za-z0-9]+)".toRegex().find(response.text)?.groupValues?.getOrNull(1)
        }
        if (!kaken.isNullOrBlank()) {
            val apiUrl = "$mainUrl/api/?$kaken=&_=${APIHolder.unixTimeMS}"
            val json = app.get(apiUrl, referer = url).parsedSafe<GWResponse>()
            json?.sources?.map { s ->
                val file = s.file ?: return@map
                val label = s.label ?: ""
                val quality = getQualityFromName(label)
                callback.invoke(
                    ExtractorLink(
                        name,
                        "$name ${if (quality != Qualities.Unknown.value) label else ""}".trim(),
                        file,
                        referer ?: mainUrl,
                        quality
                    )
                )
            }
            json?.tracks?.map { t ->
                subtitleCallback.invoke(
                    SubtitleFile(
                        t.label ?: return@map,
                        t.file ?: return@map
                    )
                )
            }
            if (json != null) return
        }

        // Fallback: parse unpacked script for sources/m3u8 if API path fails
        val script = run {
            val unpacked = getAndUnpack(response.text)
            if (!unpacked.isNullOrBlank()) unpacked
            else response.document.selectFirst("script:containsData(sources:)")?.data() ?: response.text
        }
        val m3u8 = Regex("file:\\s*\\\"(.*?m3u8.*?)\\\"").find(script)?.groupValues?.getOrNull(1)
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
        }
        val srcRegex = Regex("\\{\\s*(?:\\\"|)file(?:\\\"|)\\s*:\\s*(?:\\\"|')(.*?)(?:\\\"|')\\s*,\\s*(?:\\\"|)label(?:\\\"|)\\s*:\\s*(?:\\\"|')(.*?)(?:\\\"|')")
        srcRegex.findAll(script).forEach { match ->
            val file = match.groupValues.getOrNull(1) ?: return@forEach
            val label = match.groupValues.getOrNull(2) ?: ""
            val quality = getQualityFromName(label)
            callback.invoke(
                ExtractorLink(
                    name,
                    "$name ${if (quality != Qualities.Unknown.value) label else ""}".trim(),
                    file,
                    referer ?: mainUrl,
                    quality
                )
            )
        }
        val trackRegex = Regex("\\{\\s*(?:\\\"|)file(?:\\\"|)\\s*:\\s*(?:\\\"|')(.*?\\.(?:vtt|srt))(?:\\\"|')\\s*,\\s*(?:\\\"|)label(?:\\\"|)\\s*:\\s*(?:\\\"|')(.*?)(?:\\\"|')")
        trackRegex.findAll(script).forEach { sub ->
            val file = sub.groupValues.getOrNull(1) ?: return@forEach
            val label = sub.groupValues.getOrNull(2) ?: ""
            subtitleCallback.invoke(SubtitleFile(label, file))
        }
    }
}
