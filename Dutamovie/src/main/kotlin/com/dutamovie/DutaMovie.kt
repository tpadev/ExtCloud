package com.dutamovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI
import org.jsoup.nodes.Element

class DutaMovie : MainAPI() {

    override var mainUrl = "https://promadurafloors.com"
    private var directUrl: String? = null
    override var name = "DutaMovie"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
            setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
            mainPageOf(
                    "category/box-office/page/%d/" to "Box Office",
                    "category/serial-tv/page/%d/" to "Serial TV",
                    "category/animation/page/%d/" to "Animasi",
                    "country/korea/page/%d/" to "Serial TV Korea",
                    "country/indonesia/page/%d/" to "Serial TV Indonesia",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr()).fixImageQuality()
        val quality =
                this.select("div.gmr-qual, div.gmr-quality-item > a").text().trim().replace("-", "")
        return if (quality.isEmpty()) {
            val episode =
                    Regex("Episode\\s?([0-9]+)")
                            .find(title)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toIntOrNull()
                            ?: this.select("div.gmr-numbeps > span").text().toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document =
                app.get("${mainUrl}?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L)
                        .document
        val results = document.select("article.item").mapNotNull { it.toSearchResult() }
        return results
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("a > span.idmuvi-rp-title")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr().fixImageQuality())
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun load(url: String): LoadResponse {
        val fetch = app.get(url)
        directUrl = getBaseUrl(fetch.url)
        val document = fetch.document

        val title =
                document.selectFirst("h1.entry-title")
                        ?.text()
                        ?.substringBefore("Season")
                        ?.substringBefore("Episode")
                        ?.trim()
                        .toString()
        val poster =
                fixUrlNull(document.selectFirst("figure.pull-left > img")?.getImageAttr())
                        ?.fixImageQuality()
        val tags =
                document.select("div.gmr-moviedata strong:contains(Genre:) > a").map { it.text() }

        val year =
                document.select("div.gmr-moviedata strong:contains(Year:) > a")
                        .text()
                        .trim()
                        .toIntOrNull()
        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div[itemprop=description] > p")?.text()?.trim()
        val trailer = document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")?.attr("href")
        val rating =
                document.selectFirst("div.gmr-meta-rating > span[itemprop=ratingValue]")
                        ?.text()
                        ?.toRatingInt()
        val actors =
                document.select("div.gmr-moviedata").last()?.select("span[itemprop=actors]")?.map {
                    it.select("a").text()
                }

        val recommendations =
                document.select("div.idmuvi-rp ul li").mapNotNull { it.toRecommendResult() }

        return if (tvType == TvType.TvSeries) {
            val episodes =
                    document.select("div.vid-episodes a, div.gmr-listseries a")
                            .map { eps ->
                                val href = fixUrl(eps.attr("href"))
                                val name = eps.text()
                                val episode =
                                        name.split(" ")
                                                .lastOrNull()
                                                ?.filter { it.isDigit() }
                                                ?.toIntOrNull()
                                val season =
                                        name.split(" ")
                                                .firstOrNull()
                                                ?.filter { it.isDigit() }
                                                ?.toIntOrNull()
                                newEpisode(href) {
                                    this.name = name
                                    this.season = if (name.contains(" ")) season else null
                                    this.episode = episode
                                }
                            }
                            .filter { it.episode != null }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
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

        val document = app.get(data).document
        val id = document.selectFirst("div#muvipro_player_content_id")?.attr("data-id")

        if (id.isNullOrEmpty()) {
            document.select("ul.muvipro-player-tabs li a").apmap { ele ->
                val iframe =
                        app.get(fixUrl(ele.attr("href")))
                                .document
                                .selectFirst("div.gmr-embed-responsive iframe")
                                .getIframeAttr()
                                ?.let { httpsify(it) }
                                ?: return@apmap
                val refererBase = runCatching { getBaseUrl(iframe) }.getOrDefault(directUrl ?: "") + "/"
                // Try direct JW parse first with server origin as referer
                if (!tryDirectJW(iframe, refererBase, subtitleCallback, callback)) {
                    loadExtractor(iframe, refererBase, subtitleCallback, callback)
                }
            }
        } else {
            document.select("div.tab-content-ajax").apmap { ele ->
                val server =
                        app.post(
                                        "$directUrl/wp-admin/admin-ajax.php",
                                        data =
                                                mapOf(
                                                        "action" to "muvipro_player_content",
                                                        "tab" to ele.attr("id"),
                                                        "post_id" to "$id"
                                                )
                                )
                                .document
                                .select("iframe")
                                .attr("src")
                                .let { httpsify(it) }
                val refererBase = runCatching { getBaseUrl(server) }.getOrDefault(directUrl ?: "") + "/"
                // Try direct JW parse first with server origin as referer
                if (!tryDirectJW(server, refererBase, subtitleCallback, callback)) {
                    loadExtractor(server, refererBase, subtitleCallback, callback)
                }
            }
        }

        return true
    }

    private suspend fun tryDirectJW(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Handle shorteners/hash-driven players before fetching
        val preResolved = resolveSpecialUrls(url, referer)
        val targetUrl = preResolved ?: url

        return runCatching {
            val res = app.get(targetUrl, referer = referer)
            val body = res.text
            // Common jwplayer patterns: sources: [{file:"...m3u8"}], or file:"..."
            val fileRegexes = listOf(
                Regex("file\\s*:\\s*\\\"(https?://[^\\\"]+m3u8[^\\\"]*)\\\"", RegexOption.IGNORE_CASE),
                Regex("sources\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
            )
            val m3u8 = fileRegexes.firstNotNullOfOrNull { rx ->
                val m = rx.find(body)
                when {
                    m == null -> null
                    rx.pattern.contains("sources") -> Regex("file\\s*:\\s*\\\"([^\\\"]+)\\\"")
                        .find(m.groupValues.getOrNull(1) ?: "")
                        ?.groupValues?.getOrNull(1)
                    else -> m.groupValues.getOrNull(1)
                }
            }

            if (!m3u8.isNullOrBlank()) {
                val link = absolutize(m3u8, targetUrl)
                // If it's mp4, push as direct; if m3u8, expand
                if (link.endsWith(".mp4", true)) {
                    callback.invoke(
                        ExtractorLink(
                            name,
                            name,
                            link,
                            referer ?: targetUrl,
                            Qualities.Unknown.value,
                            type = ExtractorLinkType.VIDEO,
                        )
                    )
                } else {
                    M3u8Helper.generateM3u8(name, link, mainUrl).forEach(callback)
                }
                true
            } else {
                // Also try <source src="...">
                val src = Regex("<source[^>]+src=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.getOrNull(1)
                if (!src.isNullOrBlank()) {
                    val link = absolutize(src, targetUrl)
                    if (link.contains("m3u8", true)) {
                        M3u8Helper.generateM3u8(name, link, mainUrl).forEach(callback)
                    } else {
                        callback.invoke(
                            ExtractorLink(
                                name,
                                name,
                                link,
                                referer ?: targetUrl,
                                Qualities.Unknown.value,
                                type = ExtractorLinkType.VIDEO,
                            )
                        )
                    }
                    true
                } else {
                    // Fallback: fetch external scripts referenced by the embed page and scan for URLs
                    val doc = res.document
                    val scriptContents = mutableListOf<String>()
                    // Inline scripts
                    scriptContents += doc.select("script:not([src])").map { it.data() }
                    // External scripts
                    doc.select("script[src]").map { it.absUrl("src") }.distinct().forEach { s ->
                        runCatching { app.get(s, referer = targetUrl).text }.getOrNull()?.let { scriptContents += it }
                    }

                    var foundAny = false
                    scriptContents.forEach { code ->
                        // 1) direct urls
                        Regex("https?://[^'\"\\s]+|//[^'\"\\s]+|/[^'\"\\s]+", RegexOption.IGNORE_CASE).findAll(code).forEach { m ->
                            val u = absolutize(m.value, targetUrl)
                            if (u.contains("m3u8", true)) {
                                runCatching { M3u8Helper.generateM3u8(name, u, mainUrl).forEach(callback) }
                                foundAny = true
                            } else if (u.endsWith(".mp4", true)) {
                                callback.invoke(
                                    ExtractorLink(name, name, u, referer ?: targetUrl, Qualities.Unknown.value, type = ExtractorLinkType.VIDEO)
                                )
                                foundAny = true
                            }
                        }
                        // 2) base64 atob('...') that contains urls
                        Regex("atob\\(['\"]([A-Za-z0-9+/=]+)['\"]\\)").findAll(code).forEach { b ->
                            val b64 = b.groupValues.getOrNull(1)
                            if (!b64.isNullOrBlank()) {
                                runCatching {
                                    val decoded = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                                    Regex("https?://[^'\"\\s]+|//[^'\"\\s]+|/[^'\"\\s]+", RegexOption.IGNORE_CASE).findAll(decoded).forEach { m2 ->
                                        val u = absolutize(m2.value, targetUrl)
                                        if (u.contains("m3u8", true)) {
                                            M3u8Helper.generateM3u8(name, u, mainUrl).forEach(callback)
                                            foundAny = true
                                        } else if (u.endsWith(".mp4", true)) {
                                            callback.invoke(
                                                ExtractorLink(name, name, u, referer ?: targetUrl, Qualities.Unknown.value, type = ExtractorLinkType.VIDEO)
                                            )
                                            foundAny = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                    foundAny
                }
            }
        }.getOrElse { false }
    }

    private fun absolutize(u: String, base: String): String {
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        return when {
            u.startsWith("//") -> "https:" + u
            u.startsWith("/") -> getBaseUrl(base).trimEnd('/') + u
            else -> u
        }
    }

    private suspend fun resolveSpecialUrls(url: String, referer: String?): String? {
        // Unwrap shorteners and hash-based players where we know simple transformations
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return null
            // Shortener: short.icu – often meta refresh / JS redirect
            if (host.contains("short.icu", true)) {
                val r = app.get(url, referer = referer)
                val refreshed = r.document.selectFirst("meta[http-equiv=refresh i]")?.attr("content")
                    ?.substringAfter("url=", "")?.trim()
                val jsRedir = Regex("location\\.(href|replace)\\s*=\\s*['\"](.*?)['\"]", RegexOption.IGNORE_CASE)
                    .find(r.text)?.groupValues?.getOrNull(2)
                return listOf(refreshed, jsRedir, r.url).firstOrNull { !it.isNullOrBlank() }
            }
            // streamcasthub with hash fragment – try common patterns
            if (host.contains("streamcasthub", true) && url.contains('#')) {
                val token = url.substringAfter('#').trim()
                val candidates = listOf(
                    "https://${host}/hls/${token}.m3u8",
                    "https://${host}/stream/${token}",
                    "https://${host}/get?v=${token}",
                    "https://${host}/api/source/${token}",
                )
                // Return first that responds with 200
                for (c in candidates) {
                    val ok = runCatching { app.head(c).code == 200 }.getOrDefault(false)
                    if (ok) return c
                }
                return null
            }
            // gofile links can be sent to extractor path; but we just return as-is
            if (host.contains("gofile.io", true)) return url
            null
        } catch (_: Throwable) { null }
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src").takeIf { it?.isNotEmpty() == true }
                ?: this?.attr("src")
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}
