package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Bestx
import com.lagradost.cloudstream3.extractors.Chillx
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities

class Archivd : ExtractorApi() {
    override val name: String = "Archivd"
    override val mainUrl: String = "https://archivd.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).document
        val json = res.select("div#app").attr("data-page")
        val video = AppUtils.tryParseJson<Sources>(json)?.props?.datas?.data?.link?.media ?: return

        // gunakan newExtractorLink dengan initializer untuk set referer/quality/headers
        val link = newExtractorLink(
            source = this.name,
            name = this.name,
            url = video,
            type = INFER_TYPE
        ) {
            this.referer = "$mainUrl/"
            this.quality = Qualities.Unknown.value
        }

        callback.invoke(link)
    }

    data class Link(
        @JsonProperty("media") val media: String? = null,
    )

    data class Data(
        @JsonProperty("link") val link: Link? = null,
    )

    data class Datas(
        @JsonProperty("data") val data: Data? = null,
    )

    data class Props(
        @JsonProperty("datas") val datas: Datas? = null,
    )

    data class Sources(
        @JsonProperty("props") val props: Props? = null,
    )
}

class Newuservideo : ExtractorApi() {
    override val name = "Uservideo"
    override val mainUrl = "https://new.uservideo.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframe = app.get(url, referer = referer).document.select("iframe#videoFrame").attr("src")
        val doc = app.get(iframe, referer = "$mainUrl/").text
        val json = "VIDEO_CONFIG\\s?=\\s?(.*)".toRegex().find(doc)?.groupValues?.get(1)
        val sources = AppUtils.tryParseJson<Sources>(json) ?: return

        sources.streams?.forEach { stream ->
            val playUrl = stream.playUrl ?: return@forEach
            val qualityValue = when (stream.formatId) {
                18 -> Qualities.P360.value
                22 -> Qualities.P720.value
                else -> Qualities.Unknown.value
            }

            val link = newExtractorLink(
                source = this.name,
                name = this.name,
                url = playUrl,
                type = INFER_TYPE
            ) {
                this.referer = "$mainUrl/"
                this.quality = qualityValue
            }

            callback.invoke(link)
        }
        
    }

    data class Streams(
        @JsonProperty("play_url") val playUrl: String? = null,
        @JsonProperty("format_id") val formatId: Int? = null,
    )

    data class Sources(
        @JsonProperty("streams") val streams: ArrayList<Streams>? = null,
    )

}

class Vidhidepro : Filesim() {
    override val mainUrl = "https://vidhidepro.com"
    override val name = "Vidhidepro"
}

class Vectorx : Chillx() {
    override val mainUrl = "https://vectorx.top"
    override val name = "Vectorx"
}
