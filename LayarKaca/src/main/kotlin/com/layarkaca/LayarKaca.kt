package com.layarkaca

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class LayarKaca : MainAPI() {
    override var mainUrl = "https://tv.lk21official.love"
    private var seriesUrl = "https://tv1.nontondrama.my"
    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ---------------- MAIN PAGE ----------------
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Film Terbaru",
        "$mainUrl/category/action/page/" to "Action Terbaru",
        "$mainUrl/category/horror/page/" to "Horror Terbaru",
        "$mainUrl/category/romance/page/" to "Romance Terbaru",
        "$mainUrl/category/comedy/page/" to "Comedy Terbaru",
        "$seriesUrl/series/page/" to "Series Update",
        "$seriesUrl/populer/page/" to "Series Unggulan",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document

        // Support both LK21 (article-based) and "ml-item" card grids
        val home = document.select("div.ml-item, article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    // ---------------- SEARCH ----------------
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article, div.ml-item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Try ML-style cards first
        val mlTitle = this.select("a").attr("oldtitle").ifBlank {
            this.selectFirst(".mli-info h2, h2 a, h1 a")?.text() ?: ""
        }.trim()
        val mlHref = this.selectFirst("a")?.attr("href")
        val mlPoster = this.select("img").attr("data-original").ifBlank {
            this.select("img").attr("data-src").ifBlank {
                this.selectFirst("img")?.attr("src") ?: ""
            }
        }

        val title = mlTitle.ifBlank { null } ?: return null
        val href = fixUrl(mlHref ?: return null)
        val poster = fixUrlNull(mlPoster)

        val type = if (href.contains(seriesUrl)) TvType.TvSeries else TvType.Movie

        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        }
    }

    // ---------------- LOAD DETAIL ----------------
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val titleRaw = document.selectFirst("h1")?.text()?.trim() ?: return null
        val title = titleRaw.substringBefore("(").trim()
        val year = Regex("\\((\\d{4})\\)").find(titleRaw)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val poster = fixUrlNull(
            document.selectFirst(".thumb img, img.img-thumbnail, meta[property=og:image]")?.attr("src")
        )

        val description = document.selectFirst("div.desc, .entry-content, .mvici-right p")?.text()?.trim()
        val rating = document.selectFirst("span[itemprop=ratingValue]")?.text()?.toRatingInt()

        val tags = document.select("p:contains(Genre) a, .mvici-left p:contains(Genre) a").map { it.text() }
        val actors = document.select("p:contains(Bintang Film) a, .mvici-left p:contains(Bintang Film) a").map { it.text() }

        // cek episode (series)
        val episodes = document.select("div#seasons a, .les-content a").mapIndexed { index, el ->
            val epUrl = fixUrl(el.attr("href"))
            val epName = el.text().trim()
            newEpisode(epUrl) {
                name = epName
                season = 1
                episode = index + 1
                posterUrl = poster
                runTime = null
            }
        }

        return if (episodes.isNotEmpty()) {
            // Series
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
            }
        } else {
            // Movie
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
            }
        }
    }

    // ---------------- LOAD LINKS ----------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val iframe = document.selectFirst("iframe#main-player")?.attr("src") ?: return false
        loadExtractor(fixUrl(iframe), data, subtitleCallback, callback)
        return true
    }
}
