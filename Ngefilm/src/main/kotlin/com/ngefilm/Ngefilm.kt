package com.ngefilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

class Ngefilm : MainAPI() {
    override var mainUrl = "https://new18.ngefilm.site"
    override var name = "Ngefilm"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Upload Terbaru",
        "$mainUrl/?s=&search=advanced&post_type=tv&index=&orderby=&genre=&movieyear=&country=&quality=" to "Semua Series",
        "$mainUrl/?s=&search=advanced&post_type=tv&index=&orderby=&genre=drama&movieyear=&country=korea&quality=" to "Drama Korea",
        "$mainUrl/?s=&search=advanced&post_type=tv&index=&orderby=&genre=&movieyear=&country=indonesia&quality=" to "Series Indonesia",
        "$mainUrl/country/indonesia/" to "Film Indonesia",
    )

    // --- Ambil list film/series di halaman utama ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}/page/$page"
        val doc = app.get(url).document

        val items = doc.select("#gmr-main-load .ml-item").mapNotNull {
            val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = it.selectFirst("h2")?.text()
                ?: it.selectFirst("h3")?.text()
                ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("data-original")
                ?: it.selectFirst("img")?.attr("src")
            val quality = it.selectFirst(".mli-quality")?.text()
            val trailer = it.selectFirst("a[href*=\"youtube\"]")?.attr("href")

            newMovieSearchResponse(title, link, TvType.Movie) {
                this.posterUrl = poster
                this.quality = getQualityFromString(quality)
            }
        }

        return newHomePageResponse(request.name, items)
    }

    // --- Load detail film/series ---
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text() ?: return null
        val poster = doc.selectFirst(".thumb img")?.attr("src")
        val plot = doc.selectFirst(".desc")?.text()
        val year = doc.selectFirst("span[itemprop=dateCreated]")?.text()?.toIntOrNull()
        val isSeries = doc.select("div.gmr-listseries a").isNotEmpty()

        val trailer = doc.selectFirst("iframe[src*=\"youtube\"]")?.attr("src")

        return if (isSeries) {
            val episodes = doc.select("div.gmr-listseries a").mapIndexed { idx, el ->
                newEpisode(el.attr("href")) {
                    name = el.text().ifBlank { "Episode ${idx + 1}" }
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                addTrailer(trailer)
            }
        } else {
            val servers = doc.select("ul.muvipro-player-tabs li a").mapIndexed { idx, el ->
                newEpisode(el.attr("href")) {
                    name = "Server ${idx + 1}"
                }
            }
            newMovieLoadResponse(title, url, TvType.Movie, servers) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                addTrailer(trailer)
            }
        }
    }

    // --- Ambil link video ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        val serverLinks = doc.select("ul.muvipro-player-tabs li a").map { it.attr("href") }

        if (serverLinks.isNotEmpty()) {
            serverLinks.forEach { link ->
                val serverDoc = app.get(link).document
                val iframe = serverDoc.selectFirst("iframe")?.attr("src")
                if (!iframe.isNullOrBlank()) {
                    loadExtractor(iframe, link, subtitleCallback, callback)
                }
            }
        } else {
            val iframe = doc.selectFirst("iframe")?.attr("src")
            if (!iframe.isNullOrBlank()) {
                loadExtractor(iframe, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
