package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element

class LayarKacaProvider : MainAPI() {
    // Domain baru
    override var mainUrl = "https://tv.lk21official.love"
    private var seriesUrl = "https://tv1.nontondrama.my"
    private var searchUrl = "https://search.lk21.party"

    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Terpopuler",
        "$mainUrl/rating/page/" to "IMDb Rating",
        "$mainUrl/most-commented/page/" to "Komentar Terbanyak",
        "$seriesUrl/latest-series/page/" to "Series Terbaru",
        "$seriesUrl/series/asian/page/" to "Drama Asia",
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article figure").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h3")?.ownText()?.trim() ?: return null
        val href = fixUrl(selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(select("img").attr("src"))
        val type = if (selectFirst("span.episode") == null) TvType.Movie else TvType.TvSeries

        return if (type == TvType.TvSeries) {
    val episode = this.selectFirst("span.episode strong")?.text()?.filter { it.isDigit() }
        ?.toIntOrNull()
    newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
        this.posterUrl = posterUrl
        this.year = null
    }
} else {
    val quality = this.select("div.quality").text().trim()
    newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
        addQuality(quality)
    }
}

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$searchUrl/search.php?s=$query").text
        val results = mutableListOf<SearchResponse>()
        val root = JSONObject(res)
        val arr = root.getJSONArray("data")

        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val title = item.getString("title")
            val slug = item.getString("slug")
            val type = item.getString("type")
            val posterUrl = "https://poster.lk21.party/wp-content/uploads/" +
                item.optString("poster")

            when (type) {
                "series" -> results.add(
                    newTvSeriesSearchResponse(title, "$seriesUrl/$slug", TvType.TvSeries) {
                        this.posterUrl = posterUrl
                    }
                )
                "movie" -> results.add(
                    newMovieSearchResponse(title, "$mainUrl/$slug", TvType.Movie) {
                        this.posterUrl = posterUrl
                    }
                )
            }
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("div.movie-info h1")?.text()?.trim().orEmpty()
        val poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.tag-list span").map { it.text() }
        val year = document.select("span.year").text().toIntOrNull()
        val description = document.selectFirst("div.meta-info")?.text()?.trim()
            ?.substringBefore("Subtitle")
        val trailer = document.selectFirst("ul.action-left li a[href*=youtube]")
            ?.attr("href")
        val rating = document.selectFirst("div.info-tag strong")?.text()?.toRatingInt()

        val recommendations = document.select("li.slider article").map {
            val recName = it.selectFirst("h3")?.text()?.trim().orEmpty()
            val recHref = fixUrl(it.selectFirst("a")!!.attr("href"))
            val recPosterUrl = fixUrl(it.selectFirst("img")?.attr("src").orEmpty())
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (url.startsWith(seriesUrl)) {
            // SERIES
            val json = document.selectFirst("script#season-data")?.data()
            val episodes = mutableListOf<Episode>()
            if (json != null) {
                val root = JSONObject(json)
                root.keys().forEach { seasonKey ->
                    val seasonArr = root.getJSONArray(seasonKey)
                    for (i in 0 until seasonArr.length()) {
                        val ep = seasonArr.getJSONObject(i)
                        val href = fixUrl("$seriesUrl/${ep.getString("slug")}")
                        val episodeNo = ep.optInt("episode_no")
                        val seasonNo = ep.optInt("s")
                        episodes.add(
                            newEpisode(href) {
                                this.name = "Episode $episodeNo"
                                this.season = seasonNo
                                this.episode = episodeNo
                            }
                        )
                    }
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            // MOVIE
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
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
        val players = document.select("ul#player-list > li a").mapNotNull {
            it.attr("data-url").ifBlank { it.attr("href") }.takeIf { u -> u.isNotBlank() }
        }

        val blacklist = listOf("short.icu", "abbys")

        players.amap { realUrl ->
            if (blacklist.any { realUrl.contains(it, ignoreCase = true) }) {
                Log.d("Layarkaca", "Skip blacklisted: $realUrl")
                return@amap
            }
            Log.d("Layarkaca", "Process: $realUrl")
            loadExtractor(realUrl, data, subtitleCallback, callback)
        }
        return true
    }
}
