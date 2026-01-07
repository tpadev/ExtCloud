package com.pusatfilm

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.EmturbovidExtractor
import com.lagradost.cloudstream3.utils.loadExtractor


class Kotakajaib : ExtractorApi() {
    override val name = "Kotakajaib"
    override val mainUrl = "https://kotakajaib.me"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val doc = app.get(url, referer = referer).document
            doc.select("ul#dropdown-server li a").forEach { a ->
                val frame = a.attr("data-frame")
                if (frame.isNullOrBlank()) return@forEach

                runCatching {
                    val decoded = base64Decode(frame)
                    loadExtractor(decoded, url, subtitleCallback, callback)
                }
            }

            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNullOrBlank()) return@forEach
                runCatching {
                    loadExtractor(src, url, subtitleCallback, callback)
                }
            }
        }

        val fileId = url
            .substringBefore("?")
            .trimEnd('/')
            .substringAfterLast("/")
            .trim()

        if (fileId.isBlank()) return

        val apiUrl = "$mainUrl/api/file/$fileId/download"
        val json = app.get(apiUrl, referer = url)
            .parsedSafe<KotakajaibApi>() ?: return

        json.result?.mirrors.orEmpty().forEach { mirror ->
            val server = mirror.server.trim().lowercase()
            if (server.isBlank()) return@forEach

            mirror.resolution.orEmpty().forEach { quality ->
                if (quality <= 0) return@forEach

                val mirrorUrl = "$mainUrl/mirror/$server/$fileId/$quality"

                runCatching {
                    loadExtractor(
                        mirrorUrl,
                        url,
                        subtitleCallback,
                        callback
                    )
                }
            }
        }
    }
}


data class KotakajaibApi(
    val status: String? = null,
    val result: KotakajaibResult? = null
)

data class KotakajaibResult(
    val title: String? = null,
    val mirrors: List<KotakajaibMirror>? = null
)

data class KotakajaibMirror(
    val server: String = "",
    val resolution: List<Int>? = null
)



class Emturbovid : EmturbovidExtractor() {
    override var name = "Emturbovid"
    override var mainUrl = "https://emturbovid.com"
}

