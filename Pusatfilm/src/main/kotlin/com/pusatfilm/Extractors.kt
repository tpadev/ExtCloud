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

        val apiRes = app.get(apiUrl, referer = url)
        println("Kotakajaib apiStatus=${apiRes.code}")

        val json = apiRes.parsedSafe<KotakajaibApi>() ?: run {
            println("Kotakajaib: parsedSafe returned null")
            return
        }

        val mirrors = json.result?.mirrors.orEmpty()
        println("Kotakajaib mirrorsCount=${mirrors.size}")

        mirrors.forEach { mirror ->
            val server = mirror.server.trim().lowercase()
            val qualities = mirror.resolution.orEmpty()

            println("Kotakajaib mirror server=$server qualities=$qualities")

            if (server.isBlank()) return@forEach

            qualities.forEach { quality ->
                if (quality <= 0) return@forEach

                val mirrorUrl = "$mainUrl/mirror/$server/$fileId/$quality"
                val commonHeaders = mapOf("Referer" to url)
                val finalUrl = runCatching {
                    followRedirects(
                        startUrl = mirrorUrl,
                        referer = url,
                        headers = commonHeaders,
                        maxHops = 10
                    )
                }.getOrNull()
                println("Kotakajaib mirrorUrl=$mirrorUrl")
                println("Kotakajaib finalUrl=${finalUrl ?: "-"}")
                runCatching {
                    loadExtractor(
                        finalUrl ?: mirrorUrl,
                        url,
                        subtitleCallback,
                        callback
                    )
                }.onFailure {
                    runCatching {
                        loadExtractor(mirrorUrl, url, subtitleCallback, callback)
                    }
                }
            }
        }
    }

    private suspend fun followRedirects(
        startUrl: String,
        referer: String?,
        headers: Map<String, String> = emptyMap(),
        maxHops: Int = 10
    ): String? {
        var currentUrl = startUrl

        repeat(maxHops) { hop ->
            val res = app.get(
                currentUrl,
                referer = referer,
                headers = headers,
                allowRedirects = false
            )

            val code = res.code
            val location = res.headers["Location"] ?: res.headers["location"]

            // Debug log (hapus kalau sudah stabil)
            println("Kotakajaib redirect hop=$hop code=$code url=$currentUrl loc=${location ?: "-"}")

            if (code in 300..399 && !location.isNullOrBlank()) {
                currentUrl = if (location.startsWith("http")) {
                    location
                } else {
                    java.net.URI(currentUrl).resolve(location).toString()
                }
            } else {
                return currentUrl
            }
        }
        return currentUrl
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
    override var mainUrl = "https://turbovidhls.com"
}

