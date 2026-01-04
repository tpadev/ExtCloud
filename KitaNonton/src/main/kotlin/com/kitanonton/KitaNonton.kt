package com.kitanonton

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class KitaNonton : MainAPI() {

    override var mainUrl = "https://operaverdi.com"
    override var name = "KitaNonton👀"
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "/" to "Terbaru",
        "/best-rating/" to "Best Rating",
        "/tv-series/" to "Tv Series",
        "/genre/action/" to "Action",
        "/genre/crime/" to "Crime",
        "/genre/adventure/" to "Adventure",
        "/genre/horror/" to "Horror",
        "/country/thailand/" to "Thailand",
        "/country/korea/" to "Korea",
        "/country/philippines/" to "Philippines",
        "/country/japan/" to "Japan"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        return newHomePageResponse(
            request.name,
            fetchSlider()
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val doc = app.get(
            "$mainUrl/page/$page/?s=$query&post_type[]=post&post_type[]=tv"
        ).document

        return doc.select("div.item, div.featured-item")
            .mapNotNull { it.toSearchResult() }
            .toNewSearchResponseList()
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a") ?: return null
        val title = a.attr("title").ifEmpty {
            selectFirst("figcaption h2")?.text() ?: return null
        }

        return newMovieSearchResponse(
            title,
            fixUrl(a.attr("href")),
            TvType.Movie
        ) {
            posterUrl = selectFirst("img")?.attr("abs:src")?.fixImage()
            addQuality(
                selectFirst(".quality-top")?.text() ?: "HD"
            )
            score = selectFirst(".rating")?.ownText()
                ?.toFloatOrNull()
                ?.let { Score.from10(it) }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.entry-title")
            ?.text()
            ?.substringBefore("Season")
            ?.substringBefore("Episode")
            ?.trim()
            ?: return newMovieLoadResponse("Unknown", url, TvType.Movie, url)

        val poster = doc.selectFirst("figure img")?.attr("abs:src")?.fixImage()
        val plot = doc.selectFirst("[itemprop=description] p")?.text()
        val tags = doc.select("strong:contains(Genre) ~ a").eachText()
        val year = doc.selectFirst("strong:contains(Year)")?.nextElementSibling()
            ?.text()?.toIntOrNull()
        val rating = doc.selectFirst("[itemprop=ratingValue]")
            ?.text()?.toFloatOrNull()
        val actors = doc.select("[itemprop=actors] a").eachText()

        val isTv = url.contains("/tv/")

        return if (!isTv) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
                this.score = rating?.let { Score.from10(it) }
                addActors(actors)
            }
        } else {
            val episodes = doc.select("a.button.button-shadow")
                .mapIndexedNotNull { index, el ->
                    val href = el.attr("href")
                    if (!href.contains("/eps/")) return@mapIndexedNotNull null
                    newEpisode(href) {
                        name = el.text()
                        episode = index + 1
                        season = 1
                    }
                }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
                this.score = rating?.let { Score.from10(it) }
                addActors(actors)
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

        doc.select("iframe").forEach {
            val src = it.attr("src").httpsify()
            loadExtractor(src, mainUrl, subtitleCallback, callback)
        }
        return true
    }

    private suspend fun fetchSlider(): List<SearchResponse> {
        val doc = app.get(mainUrl).document
        return doc.select(".slider-item").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            newMovieSearchResponse(
                a.attr("title"),
                a.attr("href"),
                TvType.Movie
            ) {
                posterUrl = it.selectFirst("img")?.attr("abs:src")
                addQuality("HD")
            }
        }
    }

    private fun String.fixImage(): String =
        replace(Regex("-\\d+x\\d+"), "")

    private fun fixUrl(url: String): String =
        if (url.startsWith("http")) url else "$mainUrl/$url"
}
