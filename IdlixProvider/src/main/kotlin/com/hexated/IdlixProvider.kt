package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.extractors.helper.AesHelper
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element
import java.net.URI
import java.time.Duration
import java.time.Instant

class IdlixProvider : MainAPI() {
    // Mirrors to try (priority order)
    private val mirrors = listOf(
        "https://tv12.idlixku.com",
        "https://tv11.idlixku.com",
        "https://tv10.idlixku.com",
        "https://tv9.idlixku.com"
    )

    // Optional relay prefix (if you run a relay/proxy that returns raw HTML)
    // Example: "https://my-relay.example.com/fetch?url="
    private val relayPrefix: String? = null

    // Cache the working domain so we don't check mirrors too often
    private var cachedDomain: String? = null
    private var cachedAt: Instant? = null
    private val cacheTtl: Duration = Duration.ofMinutes(5)

    // Initialize mainUrl at provider creation using working domain detection
    override var mainUrl: String = runBlocking {
        pickWorkingDomain() ?: mirrors.last()
    }

    private var directUrl: String = mainUrl // for building ajax endpoints etc.

    override var name = "Idlix"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Featured",
        "$mainUrl/trending/page/?get=movies" to "Trending Movies",
        "$mainUrl/trending/page/?get=tv" to "Trending TV Series",
        "$mainUrl/movie/page/" to "Movie Terbaru",
        "$mainUrl/tvseries/page/" to "TV Series Terbaru",
        "$mainUrl/network/amazon/page/" to "Amazon Prime",
        "$mainUrl/network/apple-tv/page/" to "Apple TV+ Series",
        "$mainUrl/network/disney/page/" to "Disney+ Series",
        "$mainUrl/network/HBO/page/" to "HBO Series",
        "$mainUrl/network/netflix/page/" to "Netflix Series",
    )

    // -----------------------
    // Utilities for mirror handling & request safety
    // -----------------------

    private fun replaceHost(url: String, newBase: String): String {
        return try {
            val original = URI(url)
            val base = URI(newBase)
            URI(
                base.scheme,
                base.authority,
                original.path,
                original.query,
                original.fragment
            ).toString()
        } catch (e: Exception) {
            url
        }
    }

    private fun isCachedValid(): Boolean {
        val at = cachedAt ?: return false
        return !Instant.now().isAfter(at.plus(cacheTtl))
    }

    // Try to find a working domain (not showing "Verifying you are human")
    private suspend fun pickWorkingDomain(): String? {
        if (cachedDomain != null && isCachedValid()) return cachedDomain

        for (domain in mirrors) {
            try {
                val check = app.get(domain)
                val text = check.text
                val title = check.document.select("title").text().orEmpty()

                if (check.code == 200 &&
                    !text.contains("Verifying you are human", ignoreCase = true) &&
                    !text.contains("cf-challenge", ignoreCase = true) &&
                    !title.contains("Verifying you are human", ignoreCase = true)
                ) {
                    cachedDomain = domain
                    cachedAt = Instant.now()
                    mainUrl = domain
                    directUrl = domain
                    return domain
                }
            } catch (e: Exception) {
                // ignore and try next mirror
            }
        }
        return null
    }

    // Central safe GET handler
    private suspend fun safeGet(url: String): NiceResponse {
        val useRelay = !relayPrefix.isNullOrEmpty()

        // First try normal request
        try {
            var res = app.get(url)
            val title = res.document.select("title").text().orEmpty()
            val body = res.text

            // If IUAM classic (Just a moment...), try CloudflareKiller interceptor
            if (title.contains("Just a moment", ignoreCase = true) ||
                body.contains("Just a moment", ignoreCase = true)
            ) {
                val after = app.get(url, interceptor = CloudflareKiller())
                if (after.code == 200) return after
                // else fallthrough to mirror logic
                res = after
            }

            // If modern challenge (Turnstile) or 403 -> switch mirror or relay
            if (title.contains("Verifying you are human", ignoreCase = true) ||
                body.contains("Verifying you are human", ignoreCase = true) ||
                res.code == 403
            ) {
                val newDomain = pickWorkingDomain()
                if (newDomain != null) {
                    val replaced = replaceHost(url, newDomain)
                    val secondTry = app.get(replaced)
                    if (secondTry.code == 200 &&
                        !secondTry.text.contains("Verifying you are human", ignoreCase = true)
                    ) {
                        cachedDomain = newDomain
                        cachedAt = Instant.now()
                        mainUrl = newDomain
                        directUrl = newDomain
                        return secondTry
                    } else if (useRelay) {
                        // fallback to relay if configured
                        val proxied = relayPrefix + url
                        return app.get(proxied)
                    }
                } else if (useRelay) {
                    val proxied = relayPrefix + url
                    return app.get(proxied)
                }
            }

            // Normal success or other status
            return res
        } catch (e: Exception) {
            // Network error or blocked: try mirrors, then relay
            val newDomain = pickWorkingDomain()
            if (newDomain != null) {
                val replaced = replaceHost(url, newDomain)
                try {
                    val res2 = app.get(replaced)
                    if (res2.code == 200) {
                        cachedDomain = newDomain
                        cachedAt = Instant.now()
                        mainUrl = newDomain
                        directUrl = newDomain
                        return res2
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }

            if (useRelay) {
                val proxied = relayPrefix + url
                return app.get(proxied)
            }

            throw e
        }
    }

    // A safe POST that uses safeGet logic for fallback decisions.
    // Note: We still use app.post for actual POST, but if response indicates challenge, try mirrors/relay.
    private suspend fun safePost(
        url: String,
        data: Map<String, String>,
        referer: String? = null,
        headers: Map<String, String>? = null
    ): NiceResponse {
        val useRelay = !relayPrefix.isNullOrEmpty()
        try {
            val res = app.post(url = url, data = data, referer = referer, headers = headers)
            val body = res.text
            val title = res.document.select("title").text().orEmpty()

            if (title.contains("Just a moment", ignoreCase = true) ||
                body.contains("Just a moment", ignoreCase = true)
            ) {
                val after = app.post(url = url, data = data, referer = referer, headers = headers, interceptor = CloudflareKiller())
                if (after.code == 200) return after
            }

            if (title.contains("Verifying you are human", ignoreCase = true) ||
                body.contains("Verifying you are human", ignoreCase = true) ||
                res.code == 403
            ) {
                val newDomain = pickWorkingDomain()
                if (newDomain != null) {
                    val replaced = replaceHost(url, newDomain)
                    val secondTry = app.post(url = replaced, data = data, referer = referer, headers = headers)
                    if (secondTry.code == 200 && !secondTry.text.contains("Verifying you are human", ignoreCase = true)) {
                        cachedDomain = newDomain
                        cachedAt = Instant.now()
                        mainUrl = newDomain
                        directUrl = newDomain
                        return secondTry
                    } else if (useRelay) {
                        val proxied = relayPrefix + url
                        return app.post(url = proxied, data = data, referer = referer, headers = headers)
                    }
                } else if (useRelay) {
                    val proxied = relayPrefix + url
                    return app.post(url = proxied, data = data, referer = referer, headers = headers)
                }
            }

            return res
        } catch (e: Exception) {
            val newDomain = pickWorkingDomain()
            if (newDomain != null) {
                val replaced = replaceHost(url, newDomain)
                try {
                    val res2 = app.post(url = replaced, data = data, referer = referer, headers = headers)
                    if (res2.code == 200) {
                        cachedDomain = newDomain
                        cachedAt = Instant.now()
                        mainUrl = newDomain
                        directUrl = newDomain
                        return res2
                    }
                } catch (_: Exception) {
                }
            }

            if (useRelay) {
                val proxied = relayPrefix + url
                return app.post(url = proxied, data = data, referer = referer, headers = headers)
            }

            throw e
        }
    }

    // keep a minimal cfKiller wrapper if you want direct usage
    private suspend fun cfKiller(url: String): NiceResponse {
        var doc = app.get(url)
        if (doc.document.select("title").text() == "Just a moment...") {
            doc = app.get(url, interceptor = CloudflareKiller())
        }
        return doc
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    // -----------------------
    // Provider functions (use safeGet / safePost)
    // -----------------------
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val urlParts = request.data.split("?")
        val nonPaged = request.name == "Featured" && page <= 1
        val targetUrl = if (nonPaged) {
            request.data
        } else {
            "${urlParts.first()}$page/?${urlParts.lastOrNull()}"
        }

        val req = safeGet(targetUrl)
        mainUrl = getBaseUrl(req.url)
        directUrl = mainUrl
        val document = req.document
        val home = (if (nonPaged) {
            document.select("div.items.featured article")
        } else {
            document.select("div.items.full article, div#archive-content article")
        }).mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episode/") -> {
                var title = uri.substringAfter("$mainUrl/episode/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvseries/$title"
            }

            uri.contains("/season/") -> {
                var title = uri.substringAfter("$mainUrl/season/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvseries/$title"
            }

            else -> {
                uri
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.selectFirst("h3 > a") ?: return null
        val title = a.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
        val href = getProperLink(a.attr("href"))
        val posterUrl = this.select("div.poster > img").attr("src")
        val quality = getQualityFromString(this.select("span.quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val req = safeGet("$mainUrl/search/$query")
        mainUrl = getBaseUrl(req.url)
        directUrl = mainUrl
        val document = req.document
        return document.select("div.result-item").mapNotNull {
            val a = it.selectFirst("div.title > a") ?: return@mapNotNull null
            val title = a.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
            val href = getProperLink(a.attr("href"))
            val posterUrl = it.selectFirst("img")?.attr("src").orEmpty()
            newMovieSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val request = safeGet(url)
        directUrl = getBaseUrl(request.url)
        val document = request.document
        val title =
            document.selectFirst("div.data > h1")?.text()?.replace(Regex("\\(\\d{4}\\)"), "")
                ?.trim().toString()
        val images = document.select("div.g-item")

        val poster = images
            .shuffled()
            .firstOrNull()
            ?.selectFirst("a")
            ?.attr("href")
            ?: document.select("div.poster > img").attr("src")
        val tags = document.select("div.sgeneros > a").map { it.text() }
        val year = Regex(",\\s?(\\d+)").find(
            document.select("span.date").text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val tvType = if (document.select("ul#section > li:nth-child(1)").text().contains("Episodes")
        ) TvType.TvSeries else TvType.Movie
        val description = if (tvType == TvType.Movie)
            document.select("div.wp-content > p").text().trim() else
            document.select("div.content > center > p:nth-child(3)").text().trim()
        val trailer = document.selectFirst("div.embed iframe")?.attr("src")
        val rating = document.selectFirst("span.dt_rating_vgs[itemprop=ratingValue]")
            ?.text()
            ?.toDoubleOrNull()
        val actors = document.select("div.persons > div[itemprop=actor]").map {
            Actor(it.select("meta[itemprop=name]").attr("content"), it.select("img").attr("src"))
        }
        val duration = document.selectFirst("div.extra span[itemprop=duration]")?.text()
            ?.replace(Regex("\\D"), "")
            ?.toIntOrNull() ?: 0
        val recommendations = document.select("#single_relacionados article").mapNotNull {
            val img = it.selectFirst("img") ?: return@mapNotNull null
            val recName = img.attr("alt").replace(Regex("\\(\\d{4}\\)"), "")
            val recHref = it.selectFirst("a")?.attr("href").orEmpty()
            val recPosterUrl = img.attr("src")
            newMovieSearchResponse(recName, recHref,
                if (recHref.contains("/movie/")) TvType.Movie
                else TvType.TvSeries, false
            ) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("ul.episodios > li").map {
                val href = it.select("a").attr("href")
                val name = fixTitle(it.select("div.episodiotitle > a").text().trim())
                val image = it.select("div.imagen > img").attr("src")
                val episode = it.select("div.numerando").text().replace(" ", "").split("-").last()
                    .toIntOrNull()
                val season = it.select("div.numerando").text().replace(" ", "").split("-").first()
                    .toIntOrNull()
                newEpisode(href) {
                    this.name = name
                    this.season = season
                    this.episode = episode
                    this.posterUrl = image
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.duration = duration
                this.tags = tags
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.duration = duration
                this.tags = tags
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = safeGet(data).document
        val scriptRegex = """window\.idlixNonce=['"]([a-f0-9]+)['"].*?window\.idlixTime=(\d+).*?""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val script = document.select("script:containsData(window.idlix)").toString()
        val match = scriptRegex.find(script)
        val idlixNonce = match?.groups?.get(1)?.value ?: ""
        val idlixTime = match?.groups?.get(2)?.value ?: ""

        document.select("ul#playeroptionsul > li").map {
            Triple(
                it.attr("data-post"),
                it.attr("data-nume"),
                it.attr("data-type")
            )
        }.amap { (id, nume, type) ->
            val json = safePost(
                url = "$directUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type, "_n" to idlixNonce, "_p" to id, "_t" to idlixTime
                ),
                referer = data,
                headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest")
            ).parsedSafe<ResponseHash>() ?: return@amap
            val metrix = AppUtils.parseJson<AesData>(json.embed_url).m
            val password = createKey(json.key, metrix)
            val decrypted =
                AesHelper.cryptoAESHandler(json.embed_url, password.toByteArray(), false)
                    ?.fixBloat() ?: return@amap
            Log.d("Phisher", decrypted.toJson())

            when {
                !decrypted.contains("youtube") ->
                    loadExtractor(decrypted, directUrl, subtitleCallback, callback)
                else -> return@amap
            }

        }

        return true
    }

    private fun createKey(r: String, m: String): String {
        val rList = r.split("\\x").filter { it.isNotEmpty() }.toTypedArray()
        var n = ""
        var reversedM = m.split("").reversed().joinToString("")
        while (reversedM.length % 4 != 0) reversedM += "="
        val decodedBytes = try {
            base64Decode(reversedM)
        } catch (_: Exception) {
            return ""
        }
        val decodedM = String(decodedBytes.toCharArray())
        for (s in decodedM.split("|")) {
            try {
                val index = Integer.parseInt(s)
                if (index in rList.indices) {
                    n += "\\x" + rList[index]
                }
            } catch (_: Exception) {
            }
        }
        return n
    }

    private fun String.fixBloat(): String {
        return this.replace("\"", "").replace("\\", "")
    }

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean,
        @JsonProperty("videoSource") val videoSource: String,
        @JsonProperty("securedLink") val securedLink: String?,
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
    )

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("key") val key: String,
    )

    data class AesData(
        @JsonProperty("m") val m: String,
    )
}
