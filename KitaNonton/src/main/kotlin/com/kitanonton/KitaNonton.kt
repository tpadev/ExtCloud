package com.kitanonton

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.toNewSearchResponseList
import org.jsoup.nodes.Element
import java.net.URI

class KitaNonton : MainAPI() {

    override var mainUrl = "https://operaverdi.com"
    override var name = "KitaNonton👀"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "/" to "Terbaru",
        "/latest/" to "Best Rating",
        "/tv-series/" to "Tv Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Ambil slider untuk home
        val slider = fetchSlider()
        return newHomePageResponse(request.name, slider)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val document = app.get("$mainUrl/page/$page/?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L).document
        return document.select("div.item, div.featured-item")
            .mapNotNull { it.toSearchResultFlexible() }
            .toNewSearchResponseList()
    }

    private fun Element.toSearchResultFlexible(): SearchResponse? {
        val figure = this.selectFirst("figure") ?: return null
        val title = figure.selectFirst("figcaption h2")?.text()?.trim()
            ?: figure.selectFirst("a[rel=bookmark]")?.attr("title")?.trim()
            ?: return null
        val href = fixUrl(figure.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = figure.selectFirst("a > img")?.attr("abs:src")?.fixImageQuality()
        val quality = this.select("div.quality-top a").text().trim()
        val ratingText = this.selectFirst("div.rating")?.ownText()?.trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(if (quality.isEmpty()) "HD" else quality)
            this.score = ratingText?.toFloatOrNull()?.let { Score.from10(it) }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val desktopHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9"
        )

        val document = app.get(url, headers = desktopHeaders).document

        val title = document.selectFirst("h1.entry-title")?.text()?.substringBefore("Season")?.substringBefore("Episode")?.trim().orEmpty()
        val poster = document.selectFirst("figure.pull-left > img")?.attr("abs:src")?.fixImageQuality()
        val tags = document.select("strong:contains(Genre) ~ a").eachText()
        val year = document.select("div.gmr-moviedata strong:contains(Year:) > a")?.text()?.trim()?.toIntOrNull()
        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div[itemprop=description] > p")?.text()?.trim()
        val trailer = document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")?.attr("href")
        val rating = document.selectFirst("div.gmr-meta-rating > span[itemprop=ratingValue]")?.text()?.trim()
        val actors = document.select("div.gmr-moviedata").last()?.select("span[itemprop=actors]")?.map { it.select("a").text() }
        val duration = document.selectFirst("div.gmr-moviedata span[property=duration]")?.text()?.replace(Regex("\\D"), "")?.toIntOrNull()
        val recommendations = document.select("div.item, div.featured-item").mapNotNull { it.toSearchResultFlexible() }

        return if (tvType == TvType.Movie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = rating?.toFloatOrNull()?.let { Score.from10(it) }
                addActors(actors ?: emptyList())
                this.recommendations = recommendations
                this.duration = duration ?: 0
                addTrailer(trailer)
            }
        } else {
            // TV Series
            val seriesUrl = document.selectFirst("a.button.button-shadow.active")?.attr("href") ?: url.substringBefore("/eps/")
            val seriesDoc = app.get(seriesUrl, headers = desktopHeaders).document
            val episodeElements = seriesDoc.select("div.gmr-listseries a.button.button-shadow")
            var episodeCounter = 1
            val episodes = episodeElements.mapNotNull { eps ->
                val href = fixUrl(eps.attr("href")).trim()
                val name = eps.text().trim()
                if (name.contains("View All Episodes", ignoreCase = true)) return@mapNotNull null
                if (href == seriesUrl) return@mapNotNull null
                if (!name.contains("Eps", ignoreCase = true)) return@mapNotNull null

                val regex = Regex("""S(\d+)\s*Eps""", RegexOption.IGNORE_CASE)
                val match = regex.find(name)
                val season = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                val epNum = episodeCounter++

                newEpisode(href) {
                    this.name = name
                    this.season = season
                    this.episode = epNum
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = rating?.toFloatOrNull()?.let { Score.from10(it) }
                addActors(actors ?: emptyList())
                this.recommendations = recommendations
                this.duration = duration ?: 0
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
            document.select("ul.muvipro-player-tabs li a").amap { ele ->
                val iframe = app.get(fixUrl(ele.attr("href"))).document.selectFirst("div.gmr-embed-responsive iframe")
                    .getIframeAttr()?.let { httpsify(it) } ?: return@amap
                loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
            }
        } else {
            document.select("div.tab-content-ajax").amap { ele ->
                val server = app.post(
                    "$mainUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to ele.attr("id"),
                        "post_id" to "$id"
                    )
                ).document.select("iframe").attr("src").let { httpsify(it) }
                loadExtractor(server, "$mainUrl/", subtitleCallback, callback)
            }
        }

        return true
    }

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src").takeIf { it?.isNotEmpty() == true } ?: this?.attr("src")
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }

    private fun fixUrl(url: String): String {
        return if (url.startsWith("http")) url else "$mainUrl/$url".replace("//", "/").replace("https:/", "https://")
    }

    // Ambil slider home otomatis
    private suspend fun fetchSlider(): List<SearchResponse> {
        val doc = app.get(mainUrl).document
        return doc.select("section.slider .slider-item").mapNotNull { slide ->
            val figure = slide.selectFirst("figure") ?: return@mapNotNull null
            val title = figure.selectFirst("figcaption h2")?.text()?.trim() ?: return@mapNotNull null
            val url = figure.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = figure.selectFirst("img")?.attr("abs:src")
            val quality = figure.selectFirst(".quality-top a")?.text()?.trim()
            val rating = figure.selectFirst(".rating")?.ownText()?.toFloatOrNull()
            val trailer = figure.selectFirst("a.fancybox")?.attr("href")

            newMovieSearchResponse(title, url, TvType.Movie) {
                this.posterUrl = poster
                addQuality(quality ?: "HD")
                this.score = rating?.let { Score.from10(it) }
                if (!trailer.isNullOrEmpty()) addTrailer(trailer)
            }
        }
    }
}
