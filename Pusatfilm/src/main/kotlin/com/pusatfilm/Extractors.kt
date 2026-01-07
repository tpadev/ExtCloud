package com.pusatfilm

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.utils.loadExtractor


open class Kotakajaib : ExtractorApi() {
    override val name = "Kotakajaib"
    override val mainUrl = "https://kotakajaib.me"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val links = app.get(url, referer = referer).document.select("ul#dropdown-server li a")
        for (a in links) {
    loadExtractor(
        base64Decode(a.attr("data-frame")),
        "$mainUrl/",
        subtitleCallback,
        callback
    )
        }
    val fileId = url.substringAfterLast("/")

        val apiUrl = "$mainUrl/api/file/$fileId/download"

        val json = app.get(apiUrl, referer = url)
            .parsedSafe<KotakajaibApi>() ?: return

        json.result?.mirrors?.forEach { mirror ->
            mirror.resolution.forEach { quality ->

                val directUrl = when (mirror.server.lowercase()) {
                    "pixeldrain" -> "https://pixeldrain.com/api/file/$fileId?download"
                    "gofile" -> "https://gofile.io/d/$fileId"
                    else -> return@forEach
                }

                callback(
                    newExtractorLink(
                        name,
                        "Kotakajaib ${mirror.server.uppercase()} ${quality}p",
                        directUrl,
                        mainUrl,
                        quality,
                        false
                    )
                )
            }
        }
    }
}

data class KotakajaibApi(
    val status: String?,
    val result: KotakajaibResult?
)

data class KotakajaibResult(
    val title: String?,
    val mirrors: List<KotakajaibMirror>?
)

data class KotakajaibMirror(
    val server: String,
    val resolution: List<Int>
)


class Emturbovid : EmturbovidExtractor() {
    override var name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
}

