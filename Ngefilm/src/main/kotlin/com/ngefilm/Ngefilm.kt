package com.ngefilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

class Ngefilm : MainAPI() {
    override var mainUrl = "https://new17.ngefilm.site"
    override var name = "Ngefilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Main page sections reflecting the site structure and advanced TV filters
    override val mainPage = mainPageOf(
        "page/%d/" to "Beranda",
        "?s=&search=advanced&post_type=tv&index=&orderby=&genre=drama&movieyear=&country=korea&quality=&page=%d" to "TV Korea - Drama",
        "?s=&search=advanced&post_type=tv&index=&orderby=&genre=&movieyear=&country=&quality=&page=%d" to "TV - Semua",
        "?s=&search=advanced&post_type=tv&index=&orderby=&genre=&movieyear=&country=indonesia&quality=&page=%d" to "TV Indonesia",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val url = "$mainUrl/${data.removePrefix("/")}"
        val document = app.get(url).document
        val items = document.select("article.item, div.ml-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(request.name, items, isHorizontalImages = false),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // WordPress movie cards
        selectFirst("h2.entry-title > a")?.let { a ->
            val title = a.text().trim()
            val href = fixUrl(a.attr("href"))
            val poster = fixUrlNull(selectFirst("a > img")?.getImageAttr())
            val qualityText = select("div.gmr-qual, div.gmr-quality-item > a").text().trim().replace("-", "")
            return if (qualityText.isEmpty()) {
                newAnimeSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = poster
                    addQuality(qualityText)
                }
            }
        }
        // Pencurimovie-style blocks
        selectFirst("a[oldtitle]")?.let { a ->
            val title = a.attr("oldtitle").substringBefore("(").trim()
            val href = fixUrl(a.attr("href"))
            val poster = fixUrlNull(selectFirst("a img")?.attr("data-original"))
            val quality = getQualityFromString(select("span.mli-quality").text())
            return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.quality = quality
            }
        }
        return null
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Default text search
        val searchUrl = if (query.contains("search=advanced")) {
            if (query.startsWith("http")) query else "$mainUrl/$query"
        } else {
            "$mainUrl/?s=$query"
        }
        val document = app.get(searchUrl, timeout = 50L).document
        return document.select("article.item, div.ml-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val fetch = app.get(url)
        val document = fetch.document

        val title = document.selectFirst("h1.entry-title, div.mvic-desc h3")
            ?.text()
            ?.substringBefore("Season")
            ?.substringBefore("Episode")
            ?.substringBefore("(")
            ?.trim()
            .orEmpty()

        val poster = fixUrlNull(
            document.selectFirst("figure.pull-left > img")?.getImageAttr()
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val tags = document.select("div.gmr-moviedata strong:contains(Genre:) > a, div.mvic-info p:contains(Genre) a")
            .map { it.text() }
        val year = document.select("div.gmr-moviedata strong:contains(Year:) > a").text().toIntOrNull()
        val tvType = if (url.contains("/tv/") || document.select("div.vid-episodes, div.gmr-listseries").isNotEmpty()) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div[itemprop=description] > p, div.desc p.f-desc")?.text()?.trim()
        val trailer = document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")?.attr("href")
        val rating = document.selectFirst("div.gmr-meta-rating > span[itemprop=ratingValue]")?.text()?.toRatingInt()
        val actors = document.select("div.gmr-moviedata span[itemprop=actors] a").map { it.text() }.takeIf { it.isNotEmpty() }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.vid-episodes a, div.gmr-listseries a").mapNotNull { eps ->
                val href = eps.attr("href").ifBlank { null }?.let { fixUrl(it) } ?: return@mapNotNull null
                val name = eps.text()
                val episode = name.split(" ").lastOrNull()?.filter { it.isDigit() }?.toIntOrNull()
                val season = name.split(" ").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()
                newEpisode(href) {
                    this.name = name
                    this.season = season
                    this.episode = episode
                }
            }.filter { it.episode != null }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
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
        val candidates = mutableListOf<String>()

        // Collect iframe sources
        document.select("div.gmr-embed-responsive iframe, div.movieplay iframe, iframe").forEach { el ->
            val s = listOf("src", "data-src", "data-litespeed-src")
                .firstNotNullOfOrNull { key -> el.attr(key).takeIf { it.isNotBlank() } }
                ?.let { httpsify(it) }
            if (!s.isNullOrBlank()) candidates += s
        }

        // Collect explicit gofile links/buttons if present
        document.select("a[href*='gofile.io'], a[data-src*='gofile.io'], a[data-litespeed-src*='gofile.io']").forEach { a ->
            val s = listOf("href", "data-src", "data-litespeed-src")
                .firstNotNullOfOrNull { k -> a.attr(k).takeIf { it.isNotBlank() } }
                ?.let { httpsify(it) }
            if (!s.isNullOrBlank()) candidates += s
        }

        candidates.distinct().forEach { link ->
            val refererBase = runCatching { getBaseUrl(link) }.getOrDefault(mainUrl) + "/"
            loadExtractor(link, refererBase, subtitleCallback, callback)
        }
        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}

