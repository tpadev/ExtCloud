package com.layarkaca

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class LayarKaca : MainAPI() {
    override var mainUrl = "https://tv.lk21official.love"
    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/latest" to "Terbaru",
        "$mainUrl/populer" to "Populer",
        "$mainUrl/rating" to "Top Rating",
        "$mainUrl/release" to "Urut Tahun",
        "$mainUrl/genre/action" to "Action",
        "$mainUrl/genre/comedy" to "Comedy",
        "$mainUrl/genre/drama" to "Drama",
        "$mainUrl/genre/horror" to "Horror",
        "$mainUrl/genre/romance" to "Romance",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(request.data + "/page/$page").document
        val items = doc.select("div.gallery-grid article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3.poster-title")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val poster = this.selectFirst("img")?.attr("src")
        val year = this.selectFirst("span.year")?.text()?.toIntOrNull()
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?searching=$query"
        val doc = app.get(url).document
        return doc.select("div.search-item").mapNotNull {
            val title = it.selectFirst("h2 a")?.text() ?: return@mapNotNull null
            val href = fixUrl(it.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val poster = it.selectFirst("img")?.attr("src")
            val year = it.selectFirst("span.year")?.text()?.toIntOrNull()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.year = year
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: "No Title"
        val poster = doc.selectFirst("div.poster img")?.attr("src")
        val year = doc.selectFirst("span.year")?.text()?.toIntOrNull()
        val plot = doc.selectFirst("div.entry-content p")?.text()
        val genres = doc.select("div.genres a").map { it.text() }
        val trailer = doc.selectFirst("iframe[src*=\"youtube\"]")?.attr("src")

        val isSeries = url.contains("nontondrama")

        return if (isSeries) {
            // Untuk series (drama/TV)
            val episodes = doc.select("div.episode-list a").map { ep ->
                val epTitle = ep.text()
                val epUrl = fixUrl(ep.attr("href"))
                Episode(epUrl, epTitle)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = genres
                this.trailerUrl = trailer
            }
        } else {
            // Untuk movie
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = genres
                this.trailerUrl = trailer
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframes = doc.select("iframe")
        iframes.forEach { frame ->
            val link = frame.attr("data-src").ifBlank { frame.attr("src") }
            if (link.isNotBlank()) {
                loadExtractor(link, data, subtitleCallback, callback)
            }
        }
        return true
    }
}