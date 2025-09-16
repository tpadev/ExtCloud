package com.klikxxi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

class Klikxxi : MainAPI() {
    override var mainUrl = "https://klikxxi.click"
    override var name = "Klikxxi"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "?s=&search=advanced&post_type=movie&index=&orderby=&genre=&movieyear=&country=&quality=&page=%d" to "Film",
        "category/western-series/page/%d/" to "Western Series",
        "category/india-series/page/%d/" to "India Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val url = if (data.startsWith("?")) "$mainUrl/$data" else "$mainUrl/$data"
        val document = app.get(url).document
        val items = document.select("article.mega-item, article.item, div.ml-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(request.name, items, isHorizontalImages = false),
            hasNext = items.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("a[href]") ?: return null
        val title = this.selectFirst("h2.entry-title > a, h1.grid-title > a, .content-thumbnail h2 a, a[oldtitle]")?.text()?.trim()
            ?.substringBefore("(")?.ifBlank { null } ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val poster = this.selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }
        val quality = this.select("div.quality, span.mli-quality, span.quality").text().trim()
        val isSeries = this.selectFirst("div.last-episode, span.mli-episode") != null || href.contains("/series/", true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                if (quality.isNotBlank()) addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", timeout = 50L).document
        return document.select("article.mega-item, article.item, div.ml-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title, div.mvic-desc h3")?.text()
            ?.substringBefore("Season")?.substringBefore("Episode")?.substringBefore("(")?.trim().orEmpty()
        val poster = document.selectFirst("figure.pull-left img, div.gmr-movieposter img, .poster img")?.getImageAttr()?.let { fixUrlNull(it) }
        val description = document.selectFirst("div[itemprop=description] > p, div.desc p.f-desc, div.entry-content > p")?.text()?.trim()
        val tags = document.select("div.gmr-moviedata strong:contains(Genre:) > a").map { it.text() }
        val year = document.select("div.gmr-moviedata strong:contains(Year:) > a").text().toIntOrNull()
        val trailer = document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")?.attr("href")
        val rating = document.selectFirst("div.gmr-meta-rating > span[itemprop=ratingValue]")?.text()?.toRatingInt()
        val actors = document.select("div.gmr-moviedata span[itemprop=actors] a").map { it.text() }.takeIf { it.isNotEmpty() }

        val episodesElements = document.select("div.vid-episodes a, div.gmr-listseries a")
        val tvType = if (episodesElements.isNotEmpty()) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            val episodes = episodesElements.mapNotNull { epLink ->
                val href = epLink.attr("href").takeIf { it.isNotBlank() }?.let { fixUrl(it) } ?: return@mapNotNull null
                val name = epLink.text()
                val episode = name.split(" ").lastOrNull()?.filter { it.isDigit() }?.toIntOrNull()
                val season = name.split(" ").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()
                newEpisode(href) {
                    this.name = name
                    this.season = season
                    this.episode = episode
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.year = year
                this.rating = rating
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.year = year
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

        // Ambil semua server (server 1 - 7)
        document.select("ul#playeroptionsul li a, .serverlist a, a[data-frame]").forEach { el ->
            val raw = el.attr("data-frame").ifBlank { el.attr("href") }
            if (raw.isNullOrBlank()) return@forEach

            // Decode kalau base64
            val link = if (raw.startsWith("aHR0")) base64Decode(raw) else raw

            val fixed = fixUrl(link)
            val referer = getBaseUrl(data) + "/"

            // Debug log
            println("Klikxxi found server -> $fixed")

            // Lempar ke extractor bawaan (Voe, Mixdrop, Dood, dll)
            loadExtractor(fixed, referer, subtitleCallback, callback)
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
        return runCatching { URI(url).let { "${it.scheme}://${it.host}" } }.getOrDefault(mainUrl)
    }
}