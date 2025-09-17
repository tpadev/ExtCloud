package com.layarkaca

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Emturbovid : ExtractorApi() {
    override val name = "Emturbovid"
    override val mainUrl = "https://emturbovid.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val m3u8 =
            Regex("[\"'](.*?master\\.m3u8.*?)[\"']")
                .find(response.text)
                ?.groupValues
                ?.getOrNull(1)

        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
        }
    }
}

open class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://cloud.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("id=")
        val res = app.post(
            "$mainUrl/api.php?id=$id",
            data = mapOf(
                "r" to "https://playeriframe.sbs/",
                "d" to "stream.hownetwork.xyz",
            ),
            referer = url,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<Sources>()

        res?.data?.forEach {
            callback.invoke(
                newExtractorLink(
                    source = this@Hownetwork.name,
                    name = this@Hownetwork.name,
                    url = it.file,
                    referer = url,
                    quality = getQualityFromName(it.label),
                    type = INFER_TYPE
                )
            )
        }
    }

    data class Sources(val data: ArrayList<Data>) {
        data class Data(
            val file: String,
            val label: String?,
        )
    }
}

class FileMoon : Filesim() {
    override val name = "FileMoon"
    override var mainUrl = "https://filemoon.sx"
}