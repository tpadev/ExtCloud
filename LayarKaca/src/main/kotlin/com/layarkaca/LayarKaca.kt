package com.layarkaca

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class LayarKaca : MainAPI() {
    override var mainUrl = "https://tv.lk21official.love"
    private var seriesUrl = "https://tv1.nontondrama.my"

    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/%d/" to "Film Terpopuler",
        "$mainUrl/most-commented/page/%d/" to "Film Dengan Komentar Terbanyak",
        "$mainUrl/rating/page/%d/" to "Film IMDb Rating",
        "$mainUrl/latest/page/%d/" to "Film Terbaru",
        "$seriesUrl/country/south-korea/page/%d/" to "Drama Korea",
        "$seriesUrl/country/china/page/%d/" to "Series China",
        "$seriesUrl/series/west/page/%d/" to "Series Barat",
        "$seriesUrl/populer/page/%d/" to "Series Populer",
        "$seriesUrl/latest-series/page/%d/" to "Series Terbaru",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data.format(page)).document
        val home = when {
            request.data.contains(mainUrl) -> {
                // FILM
                document.select("div.gallery-grid article").mapNotNull { it.toMovieResult() }
            }
            request.data.contains(seriesUrl) -> {
                // SERIES
                document.select("article.item, div.ml-item").mapNotNull { it.toSeriesResult() }
            }
            else -> emptyList()
        }
        return newHomePageResponse(request.name, home)
    }

    // 🔹 Parsing Movie Result
    private fun Element.toMovieResult(): SearchResponse? {
        val aTag = selectFirst("a[itemprop=url]") ?: return null
        val href = fixUrl(aTag.attr("href"))
        val title = selectFirst("h3.poster-title")?.text()?.trim() ?: return null
        val poster = selectFirst("img[itemprop=image]")?.attr("src")?.let { fixUrlNull(it) }
        val quality = selectFirst("span.label")?.text()?.trim().orEmpty()
        val year = selectFirst("span.year")?.text()?.toIntOrNull()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            this.year = year
            if (quality.isNotBlank()) addQuality(quality)
        }
    }

    // 🔹 Parsing Series Result (nontondrama)
    private fun Element.toSeriesResult(): SearchResponse? {
        val aTag = selectFirst("a") ?: return null
        val href = fixUrl(aTag.attr("href"))
        val title = selectFirst("h2.entry-title a, h1.grid-title a, h3")?.text()?.trim() ?: return null
        val poster = selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.gallery-grid article, article.item, div.ml-item")
            .mapNotNull {
                it.toMovieResult() ?: it.toSeriesResult()
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title, div.mvic-desc h3")?.text()?.trim().orEmpty()
        val poster = document.selectFirst("img[src]")?.getImageAttr()?.let { fixUrlNull(it) }
        val description = document.selectFirst("div[itemprop=description], div.desc, blockquote")?.text()?.trim()
        val tags = document.select("div.gmr-moviedata strong:contains(Genre:) a, div.content h3 a").map { it.text() }
        val year = document.select("span.year, div.gmr-moviedata strong:contains(Year:) a").text().toIntOrNull()
        val trailer = document.selectFirst("a.gmr-trailer-popup, a.fancybox")?.attr("href")
        val rating = document.selectFirst("span[itemprop=ratingValue]")?.text()?.toRatingInt()
        val actors = document.select("span[itemprop=actors] a, div.col-xs-9.content h3 a").map { it.text() }

        val episodes = document.select("div.vid-episodes a, div.gmr-listseries a, div.episode-list a")
            .mapNotNull { ep ->
                val href = fixUrl(ep.attr("href"))
                val name = ep.text()
                val episode = name.filter { it.isDigit() }.toIntOrNull()
                newEpisode(href) {
                    this.name = name
                    this.episode = episode
                }
            }

        return if (episodes.isNotEmpty()) {
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
        val iframes = document.select("iframe[src], iframe[data-src], div.gmr-embed-responsive iframe")
        iframes.forEach { iframe ->
            val src = listOf("data-src", "src").firstNotNullOfOrNull { iframe.attr(it).takeIf { v -> v.isNotBlank() } }
                ?: return@forEach
            val link = fixUrl(src)
            val referer = getBaseUrl(link) + "/"
            loadExtractor(link, referer, subtitleCallback, callback)
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
        return kotlin.runCatching { java.net.URI(url).let { "${it.scheme}://${it.host}" } }
            .getOrDefault(mainUrl)
    }
}