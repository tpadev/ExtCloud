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
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI

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
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr())
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
            app.get("${mainUrl}?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L).document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("a > span.idmuvi-rp-title")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr())
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
        val poster = fixUrlNull(document.selectFirst("figure.pull-left > img")?.getImageAttr())
        val tags = document.select("div.gmr-moviedata strong:contains(Genre:) > a").map { it.text() }

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
            document.select("div.gmr-moviedata").last()
                ?.select("span[itemprop=actors]")?.map { it.select("a").text() }

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

        // ambil iframe dari halaman film (src/data-src/data-litespeed-src)
        val iframeEl = document.selectFirst("div.gmr-embed-responsive iframe, iframe")
        val iframe = listOf("src", "data-src", "data-litespeed-src")
            .firstNotNullOfOrNull { key -> iframeEl?.attr(key)?.takeIf { it.isNotBlank() } }
            ?.let { httpsify(it) }

        if (!iframe.isNullOrBlank()) {
            val refererBase =
                runCatching { getBaseUrl(iframe) }.getOrDefault(directUrl ?: "") + "/"
            loadExtractor(iframe, refererBase, subtitleCallback, callback)
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
