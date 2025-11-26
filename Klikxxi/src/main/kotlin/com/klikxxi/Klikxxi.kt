package com.klikxxi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI

class Klikxxi : MainAPI() {

    override var mainUrl = "https://klikxxi.me"
    override var name = "Klikxxi🎭"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    /** Main page: Film Terbaru & Series Terbaru */
    override val mainPage = mainPageOf(
        "?s=&search=advanced&post_type=movie&index=&orderby=&genre=&movieyear=&country=&quality=&paged=%d" to "Film Terbaru",
        "tv/page/%d/" to "Series Terbaru",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        // Untuk halaman 1 biasanya banyak theme WordPress tidak pakai /page/1/
        val data = if (page == 1) {
            request.data
                .replace("/page/%d/", "/")     // tv/page/%d/  -> tv/
                .replace("paged=%d", "")       // paged=%d     -> ""
                .replace("&&", "&")            // bereskan double &
                .trimEnd('&', '?')
        } else {
            request.data.format(page)
        }

        val url = if (data.startsWith("http")) data else "$mainUrl/$data"
        val document = app.get(url).document

        val items = document.select("article.item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items)
    }

    /* =======================
       Search & List Handling
       ======================= */

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = selectFirst("a[href][title]") ?: return null

        val href = fixUrl(linkElement.attr("href").ifBlank {
            selectFirst("a")?.attr("href") ?: return null
        })

        val rawTitle = linkElement.attr("title")
        val title = rawTitle
            .removePrefix("Permalink to: ")
            .ifBlank { linkElement.text() }
            .trim()

        if (title.isBlank()) return null

        // Poster – support src, srcset, data-lazy-src, dll + ambil resolusi terbesar
        val posterElement = selectFirst("a img")
        val posterUrl = posterElement
            .fixPoster()
            ?.let { fixUrl(it) }

        val quality = selectFirst("span.gmr-quality-item")?.text()?.trim()
        val typeText = selectFirst(".gmr-posttype-item")?.text()?.trim()

        val isSeries = typeText.equals("TV Show", ignoreCase = true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                if (!quality.isNullOrBlank()) addQuality(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                if (!quality.isNullOrBlank()) addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", timeout = 50L).document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    /** Kadang rekomendasi punya struktur HTML beda */
    private fun Element.toRecommendResult(): SearchResponse? {
        val title = selectFirst("a > span.idmuvi-rp-title")
            ?.text()
            ?.trim()
            ?: return null

        val href = fixUrl(selectFirst("a")?.attr("href") ?: return null)

        val posterElement = selectFirst("a > img")
        val posterUrl = posterElement
            .fixPoster()
            ?.let { fixUrl(it) }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    /* =======================
       Load Detail Page
       ======================= */

    override suspend fun load(url: String): LoadResponse {
        val fetch = app.get(url)
        val document = fetch.document

        // Title tanpa Season/Episode/Year
        val title = document
            .selectFirst("h1.entry-title, div.mvic-desc h3")
            ?.text()
            ?.substringBefore("Season")
            ?.substringBefore("Episode")
            ?.substringBefore("(")
            ?.trim()
            .orEmpty()

        val poster = document
            .selectFirst("figure.pull-left > img, .mvic-thumb img, .poster img")
            .fixPoster()
            ?.let { fixUrl(it) }

        val description = document.selectFirst(
            "div[itemprop=description] > p, " +
                "div.desc p.f-desc, " +
                "div.entry-content > p"
        )
            ?.text()
            ?.trim()

        val tags = document
            .select("div.gmr-moviedata strong:contains(Genre:) > a")
            .map { it.text() }

        val year = document
            .select("div.gmr-moviedata strong:contains(Year:) > a")
            .text()
            .toIntOrNull()

        val trailer = document
            .selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")
            ?.attr("href")

        val rating = document
            .selectFirst("span[itemprop=ratingValue]")
            ?.text()
            ?.toDoubleOrNull()

        val actors = document
            .select("div.gmr-moviedata span[itemprop=actors] a")
            .map { it.text() }
            .takeIf { it.isNotEmpty() }

        val recommendations = document
            .select("div.idmuvi-rp ul li")
            .mapNotNull { it.toRecommendResult() }

        /* ===== Ambil Episodes (kalau TV Series) ===== */

        val seasonBlocks = document.select("div.gmr-season-block")
        val allEpisodes = mutableListOf<Episode>()

        seasonBlocks.forEach { block ->
            val seasonTitle = block.selectFirst("h3.season-title")?.text()?.trim()
            val seasonNumber = Regex("(\\d+)")
                .find(seasonTitle ?: "")
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: 1

            val eps = block.select("div.gmr-season-episodes a")
                .filter { a ->
                    val t = a.text().lowercase()
                    !t.contains("view all") && !t.contains("batch")
                }
                .mapIndexedNotNull { index, epLink ->
                    val hrefEp = epLink.attr("href")
                        .takeIf { it.isNotBlank() }
                        ?.let { fixUrl(it) }
                        ?: return@mapIndexedNotNull null

                    val name = epLink.text().trim()

                    val episodeNum = Regex("E(p|ps)?(\\d+)", RegexOption.IGNORE_CASE)
                        .find(name)
                        ?.groupValues
                        ?.getOrNull(2)
                        ?.toIntOrNull()
                        ?: (index + 1)

                    newEpisode(hrefEp) {
                        this.name = name
                        this.season = seasonNumber
                        this.episode = episodeNum
                    }
                }

            allEpisodes.addAll(eps)
        }

        val episodes = allEpisodes
            .sortedWith(compareBy({ it.season }, { it.episode }))

        val tvType = if (episodes.isNotEmpty()) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.year = year
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.year = year
                addActors(actors)
                addTrailer(trailer)
                if (rating != null) addScore(rating.toString(), 10)
                this.recommendations = recommendations
            }
        }
    }

    /* =======================
       Links / Streams
       ======================= */

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val postId = document
            .selectFirst("div#muvipro_player_content_id")
            ?.attr("data-id")

        if (postId.isNullOrBlank()) return false

        document.select("div.tab-content-ajax").forEach { tab ->
            val tabId = tab.attr("id")
            if (tabId.isNullOrBlank()) return@forEach

            val response = app.post(
                "$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "muvipro_player_content",
                    "tab" to tabId,
                    "post_id" to postId
                )
            ).document

            val iframe = response.selectFirst("iframe")?.getIframeAttr() ?: return@forEach
            val link = httpsify(iframe)

            loadExtractor(link, mainUrl, subtitleCallback, callback)
        }

        return true
    }

    /* =======================
       Helper Functions
       ======================= */

    /** Ambil URL poster terbaik (srcset terbesar, data-lazy-src, dst) */
    private fun Element?.fixPoster(): String? {
        if (this == null) return null

        // Prioritas: srcset (ambil terbesar) -> data-lazy-src -> data-src -> src
        var link: String? = null

        if (this.hasAttr("srcset")) {
            val srcset = this.attr("abs:srcset")
            link = srcset.split(",")
                .map { it.trim().split(" ")[0] }
                .lastOrNull()
        }

        if (link.isNullOrBlank() && this.hasAttr("data-lazy-src")) {
            link = this.attr("abs:data-lazy-src")
        }

        if (link.isNullOrBlank() && this.hasAttr("data-src")) {
            link = this.attr("abs:data-src")
        }

        if (link.isNullOrBlank()) {
            link = this.attr("abs:src")
        }

        if (link.isNullOrBlank()) return null

        // Hilangkan suffix ukuran kecil: -170x255, -300x450, dll
        link = link.fixImageQuality()

        // Jika //example.com -> https://example.com
        if (link.startsWith("//")) {
            link = "https:$link"
        }

        return link
    }

    /** Ambil src untuk iframe, support data-litespeed-src */
    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src").takeIf { !it.isNullOrEmpty() }
            ?: this?.attr("src")
    }

    /** Hapus pattern -WIDTHxHEIGHT sebelum ekstensi */
    private fun String?.fixImageQuality(): String {
        if (this == null) return ""
        val regex = Regex("-\\d+x\\d+(?=\\.(webp|jpg|jpeg|png))", RegexOption.IGNORE_CASE)
        return this.replace(regex, "")
    }

    /** Base URL dari sebuah URL (scheme + host) */
    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}
