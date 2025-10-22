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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.jsoup.nodes.Element
import java.net.URI
import java.time.Duration
import java.time.Instant

class IdlixProvider : MainAPI() {
    // ===================== KONFIGURASI DASAR =====================
    private val mirrors = listOf(
        "https://tv12.idlixku.com",
        "https://tv11.idlixku.com",
        "https://tv10.idlixku.com",
        "https://tv9.idlixku.com"
    )

    // Cloudflare relay kamu (Cloudflare Worker). Jangan sertakan tambahan "https://" lagi,
    // relayUrl(...) akan menambahkan skema yang tepat.
    private val relayPrefix: String? = "https://idlix-relay.pendisu647.workers.dev/"

    private var cachedDomain: String? = null
    private var cachedAt: Instant? = null
    private val cacheTtl: Duration = Duration.ofMinutes(10)

    override var mainUrl: String = runBlocking { pickWorkingDomain() ?: mirrors.last() }
    private var directUrl: String = mainUrl

    override var name = "Idlix"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Featured",
        "$mainUrl/trending/page/?get=movies" to "Trending Movies",
        "$mainUrl/trending/page/?get=tv" to "Trending TV Series",
        "$mainUrl/movie/page/" to "Movie Terbaru",
        "$mainUrl/tvseries/page/" to "TV Series Terbaru",
        "$mainUrl/network/netflix/page/" to "Netflix Series",
        "$mainUrl/network/disney/page/" to "Disney+ Series",
        "$mainUrl/network/HBO/page/" to "HBO Series",
    )

    // ===================== BROWSER-LIKE HEADERS =====================
    private val browserHeaders: Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9,id;q=0.8",
        "Accept-Encoding" to "gzip, deflate, br",
        "Cache-Control" to "no-cache",
        "Pragma" to "no-cache",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
        "Referer" to "https://google.com"
    )

    // ===================== MIRROR HANDLING =====================
    private fun replaceHost(url: String, newBase: String): String {
        return try {
            val original = URI(url)
            val base = URI(newBase)
            URI(base.scheme, base.authority, original.path, original.query, original.fragment).toString()
        } catch (_: Exception) { url }
    }

    private fun isCachedValid(): Boolean {
        val at = cachedAt ?: return false
        return !Instant.now().isAfter(at.plus(cacheTtl))
    }

    private suspend fun pickWorkingDomain(): String? {
        if (cachedDomain != null && isCachedValid()) return cachedDomain
        for (domain in mirrors) {
            try {
                // cek pakai browser-like headers supaya respons serupa browser
                val res = app.get(domain, headers = browserHeaders)
                val text = res.text.lowercase()
                val title = res.document.select("title").text().lowercase()
                if (res.code == 200 &&
                    !text.contains("verifying you are human") &&
                    !text.contains("just a moment") &&
                    !title.contains("verifying")) {
                    cachedDomain = domain
                    cachedAt = Instant.now()
                    mainUrl = domain
                    directUrl = domain
                    return domain
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // ===================== RELAY HELPERS =====================
    // Build worker proxied URL — prevent duplicate scheme
    private fun relayUrl(target: String): String {
        val prefix = relayPrefix ?: return target
        val stripped = target.removePrefix("https://").removePrefix("http://")
        return if (prefix.endsWith("/")) prefix + stripped else "$prefix/$stripped"
    }

    // ===================== SAFE REQUEST (GET + POST) =====================
    private suspend fun safeGet(url: String): NiceResponse {
        val useRelay = !relayPrefix.isNullOrEmpty()
        try {
            // primary attempt (with browser-like headers)
            var res = app.get(url, headers = browserHeaders)
            val title = try { res.document.select("title").text().orEmpty() } catch (_: Exception) { "" }
            val text = res.text.orEmpty()

            // classic IUAM
            if (title.contains("Just a moment", true) || text.contains("Just a moment", true)) {
                val after = app.get(url, interceptor = CloudflareKiller(), headers = browserHeaders)
                if (after.code == 200) return after
                res = after
            }

            // modern challenge or blocked
            if (title.contains("Verifying you are human", true) ||
                text.contains("Verifying you are human", true) ||
                res.code == 403
            ) {
                // try mirror
                val newDomain = pickWorkingDomain()
                if (newDomain != null) {
                    val replaced = replaceHost(url, newDomain)
                    try {
                        val res2 = app.get(replaced, headers = browserHeaders)
                        if (res2.code == 200 && !res2.text.contains("Verifying", true)) {
                            cachedDomain = newDomain
                            cachedAt = Instant.now()
                            mainUrl = newDomain
                            directUrl = newDomain
                            return res2
                        }
                    } catch (_: Exception) {}
                }

                // fallback to relay worker
                if (useRelay) {
                    val proxied = relayUrl(url)
                    return app.get(proxied, headers = browserHeaders)
                }
            }

            return res
        } catch (e: Exception) {
            // on exception try mirror then relay
            val newDomain = pickWorkingDomain()
            if (newDomain != null) {
                val replaced = replaceHost(url, newDomain)
                try {
                    val res2 = app.get(replaced, headers = browserHeaders)
                    if (res2.code == 200) {
                        cachedDomain = newDomain
                        cachedAt = Instant.now()
                        mainUrl = newDomain
                        directUrl = newDomain
                        return res2
                    }
                } catch (_: Exception) {}
            }

            if (useRelay) {
                val proxied = relayUrl(url)
                return app.get(proxied, headers = browserHeaders)
            }

            throw e
        }
    }

    private suspend fun safePost(
        url: String,
        data: Map<String, String>,
        referer: String? = null,
        headers: Map<String, String>? = null
    ): NiceResponse {
        val useRelay = !relayPrefix.isNullOrEmpty()
        val hdrs = (headers ?: emptyMap()) + browserHeaders // merge so browser headers present
        try {
            var res = app.post(url = url, data = data, referer = referer, headers = hdrs)
            val title = try { res.document.select("title").text().orEmpty() } catch (_: Exception) { "" }
            val text = res.text.orEmpty()

            if (title.contains("Just a moment", true) || text.contains("Just a moment", true)) {
                val after = app.post(url = url, data = data, referer = referer, headers = hdrs, interceptor = CloudflareKiller())
                if (after.code == 200) return after
                res = after
            }

            if (title.contains("Verifying you are human", true) || text.contains("Verifying", true) || res.code == 403) {
                val newDomain = pickWorkingDomain()
                if (newDomain != null) {
                    val replaced = replaceHost(url, newDomain)
                    try {
                        val res2 = app.post(url = replaced, data = data, referer = referer, headers = hdrs)
                        if (res2.code == 200 && !res2.text.contains("Verifying", true)) {
                            cachedDomain = newDomain
                            cachedAt = Instant.now()
                            mainUrl = newDomain
                            directUrl = newDomain
                            return res2
                        }
                    } catch (_: Exception) {}
                }
                if (useRelay) {
                    val proxied = relayUrl(url)
                    return app.post(url = proxied, data = data, referer = referer, headers = hdrs)
                }
            }

            return res
        } catch (e: Exception) {
            if (useRelay) {
                try {
                    val proxied = relayUrl(url)
                    return app.post(url = proxied, data = data, referer = referer, headers = hdrs)
                } catch (_: Exception) {}
            }
            throw e
        }
    }

    private fun getBaseUrl(url: String): String = URI(url).let { "${it.scheme}://${it.host}" }

    // ===================== MAIN PAGE PARSER =====================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val urlParts = request.data.split("?")
        val targetUrl = if (request.name == "Featured" && page <= 1)
            request.data
        else
            "${urlParts.first()}$page/?${urlParts.lastOrNull() ?: ""}"

        val res = safeGet(targetUrl)
        mainUrl = getBaseUrl(res.url)
        directUrl = mainUrl

        val document = res.document
        val items = document.select("div.items.full article, div.items.featured article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.selectFirst("h3 > a") ?: return null
        val title = a.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
        val href = a.attr("href")
        val poster = this.select("div.poster > img").attr("src")
        val quality = getQualityFromString(this.select("span.quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            posterUrl = poster
            this.quality = quality
        }
    }

    // ===================== SEARCH =====================
    override suspend fun search(query: String): List<SearchResponse> {
        val res = safeGet("$mainUrl/search/$query")
        val doc = res.document
        return doc.select("div.result-item").mapNotNull {
            val a = it.selectFirst("div.title > a") ?: return@mapNotNull null
            val title = a.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
            val href = a.attr("href")
            val poster = it.selectFirst("img")?.attr("src").orEmpty()
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        }
    }

    // ===================== LOAD DETAIL =====================
    override suspend fun load(url: String): LoadResponse {
        val res = safeGet(url)
        val doc = res.document
        val title = doc.selectFirst("div.data > h1")?.text()?.replace(Regex("\\(\\d{4}\\)"), "")?.trim() ?: "Unknown"
        val poster = doc.select("div.poster > img").attr("src")
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year = doc.select("span.date").text().filter { it.isDigit() }.toIntOrNull()
        val desc = doc.select("div.wp-content > p").text().trim()
        val trailer = doc.selectFirst("div.embed iframe")?.attr("src")
        val rating = doc.selectFirst("span.dt_rating_vgs[itemprop=ratingValue]")?.text()?.toDoubleOrNull()
        val actors = doc.select("div.persons > div[itemprop=actor]").map {
            Actor(it.select("meta[itemprop=name]").attr("content"), it.select("img").attr("src"))
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            posterUrl = poster
            plot = desc
            year?.let { this.year = it }
            tags.let { this.tags = it }
            rating?.let { addScore(it.toString(), 10) }
            addActors(actors)
            addTrailer(trailer)
        }
    }

    // ===================== LOAD LINKS (PLAYER) =====================
    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = safeGet(data).document
        val scriptRegex = """window\.idlixNonce=['"]([a-f0-9]+)['"].*?window\.idlixTime=(\d+)""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val script = document.select("script:containsData(window.idlix)").toString()
        val match = scriptRegex.find(script)
        val idlixNonce = match?.groups?.get(1)?.value ?: ""
        val idlixTime = match?.groups?.get(2)?.value ?: ""

        document.select("ul#playeroptionsul > li").amap {
            val id = it.attr("data-post")
            val nume = it.attr("data-nume")
            val type = it.attr("data-type")

            val json = safePost(
                "$directUrl/wp-admin/admin-ajax.php",
                mapOf("action" to "doo_player_ajax", "post" to id, "nume" to nume, "type" to type, "_n" to idlixNonce, "_p" to id, "_t" to idlixTime),
                referer = data,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).parsedSafe<ResponseHash>() ?: return@amap

            val metrix = AppUtils.parseJson<AesData>(json.embed_url).m
            val key = createKey(json.key, metrix)
            val decrypted = AesHelper.cryptoAESHandler(json.embed_url, key.toByteArray(), false)?.fixBloat() ?: return@amap

            if (!decrypted.contains("youtube")) {
                loadExtractor(decrypted, directUrl, subtitleCallback, callback)
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
