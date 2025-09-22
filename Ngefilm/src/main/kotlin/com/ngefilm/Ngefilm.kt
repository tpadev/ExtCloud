package com.lagradost.cloudstream3.movieproviders

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

    // Ambil list film/series di main page
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data).document
        val items = doc.select("div.ml-item").mapNotNull {
            val link = it.selectFirst("a") ?: return@mapNotNull null
            val title = it.selectFirst("h2")?.text() ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.getImageAttr()
            val quality = it.selectFirst(".mli-quality")?.text()
            val rating = it.selectFirst(".starstruck-rating")?.text()?.toDoubleOrNull()
            val trailer = it.selectFirst("a[href*=\"youtube\"]")?.attr("href") // trailer di kartu film

            newMovieSearchResponse(title, link.attr("href"), TvType.Movie) {
                this.posterUrl = poster
                this.quality = quality
                this.rating = rating
                this.trailerUrl = trailer
            }
        }
        return newHomePageResponse(request.name, items)
    }

    // Load detail film/series
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text() ?: return null
        val poster = doc.selectFirst(".thumb img")?.getImageAttr()
        val plot = doc.selectFirst(".desc")?.text()
        val year = doc.selectFirst("span[itemprop=dateCreated]")?.text()?.toIntOrNull()
        val type = if (doc.select("div.gmr-listseries a").isNotEmpty()) TvType.TvSeries else TvType.Movie

        return when (type) {
            TvType.Movie -> {
                val episodes = doc.select("ul.muvipro-player-tabs li a").mapIndexed { idx, el ->
                    Episode(
                        data = fixUrl(el.attr("href")),
                        name = "Server ${idx + 1}"
                    )
                }
                newMovieLoadResponse(title, url, TvType.Movie, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = plot
                }
            }
            TvType.TvSeries -> {
                val episodes = doc.select("div.gmr-listseries a")
                    .mapIndexed { idx, el ->
                        Episode(
                            data = fixUrl(el.attr("href")),
                            name = el.text().ifBlank { "Episode ${idx + 1}" }
                        )
                    }
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = plot
                }
            }
            else -> null
        }
    }

    // Ambil link video dari server/episode
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        val serverLinks = doc.select("ul.muvipro-player-tabs li a")
            .map { fixUrl(it.attr("href")) }

        if (serverLinks.isNotEmpty()) {
            // Series episode -> punya daftar server
            serverLinks.forEach { link ->
                val serverDoc = app.get(link).document
                val iframe = serverDoc.selectFirst("iframe")?.attr("src")
                if (!iframe.isNullOrBlank()) {
                    loadExtractor(iframe, link, subtitleCallback, callback)
                }
            }
        } else {
            // Movie / server langsung
            val iframe = doc.selectFirst("iframe")?.attr("src")
            if (!iframe.isNullOrBlank()) {
                loadExtractor(iframe, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
