package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink


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
                        "d" to "cloud.hownetwork.xyz",
                ),
                referer = url,
                headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest"
                )
        ).parsedSafe<Sources>()

        res?.data?.map {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = it.file,
                    INFER_TYPE
                ) {
                    this.referer = url
                    this.quality = getQualityFromName(it.label)
                }
            )
        }

    }

    data class Sources(
            val data: ArrayList<Data>
    ) {
        data class Data(
                val file: String,
                val label: String?,
        )
    }
}

class Short : Filesim() {
    override val name = "Short"
    override var mainUrl = "https://short.icu"
}

class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

class Emturbovid : Filesim() {
    override val name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
}