package com.filmapik

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI
import org.jsoup.nodes.Element

class Filmapik : MainAPI() {

override var mainUrl = "https://filmapik.forum"
private var directUrl: String? = null

override var name = "Filmapik🎬"
override val hasMainPage = true
override var lang = "id"

override val supportedTypes =
    setOf(TvType.Movie, TvType.TvSeries)

override val mainPage =
    mainPageOf(
        "category/box-office/page/%d/" to "Box Office",
        "tvshows/page/%d/" to "TV Shows",
        "latest/page/%d/" to "Latest Updates"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val url = "$mainUrl/${request.data.format(page)}"
    val document = app.get(url).document

    val home = document.select("article.item.movies")
        .mapNotNull { it.toSearchResult() }

    return newHomePageResponse(request.name, home)
}

    private fun Element.toSearchResult(): SearchResponse? {
    val a = selectFirst("a[title]") ?: return null
    val title = a.attr("title").trim()
    val href = fixUrl(a.attr("href"))

    // Poster
    val poster = fixUrlNull(
        selectFirst("div.poster img")?.attr("src")
    )

    // Quality (WEBDL, HD, CAM, dsb)
    val quality = selectFirst("div.mepo span.quality")
        ?.text()
        ?.trim()

    // Rating (jika ada)
    val ratingText = selectFirst("div.rating")?.ownText()?.trim()

    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = poster
        if (!quality.isNullOrBlank()) addQuality(quality)
        this.score = Score.from10(ratingText?.toDoubleOrNull())
    }
}

override suspend fun search(query: String): List<SearchResponse> {
    val url = "$mainUrl/?s=$query"
    val document = app.get(url, timeout = 50L).document

    return document.select("div.result-item article")
        .mapNotNull { it.toSearchResult() }
}


    private fun Element.toRecommendResult(): SearchResponse? {
    val a = selectFirst("article a") ?: return null

    val href = fixUrl(a.attr("href"))
    val img = a.selectFirst("img") ?: return null

    val title = img.attr("alt").trim()
    val poster = fixUrlNull(img.attr("src"))

    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = poster
    }
}



    override suspend fun load(url: String): LoadResponse {
    val fetch = app.get(url)
    val document = fetch.document

    /* ============================
       TITLE
    ============================= */
    val title = document.selectFirst("h1[itemprop=name]")
        ?.text()
        ?.trim()
        ?: "Unknown Title"

    /* ============================
       POSTER
    ============================= */
    val poster = fixUrlNull(
        document.selectFirst("div.poster img")?.attr("src")
    )

    /* ============================
       PLOT
    ============================= */
    val description = document.selectFirst("div[itemprop=description] p")
        ?.text()
        ?.trim()

    /* ============================
       TAGS / GENRE
    ============================= */
    val tags = document.select("span[itemprop=genre]")
        .map { it.text().trim() }

    /* ============================
       YEAR / RELEASE
    ============================= */
    val year = document.selectFirst("span[itemprop=release]")
        ?.text()
        ?.trim()
        ?.toIntOrNull()

    /* ============================
       RATING
    ============================= */
    val rating = document.selectFirst("span[itemprop=ratingValue]")
        ?.text()
        ?.trim()

    /* ============================
       DURATION
    ============================= */
    val duration = document.selectFirst("span[itemprop=duration]")
        ?.text()
        ?.replace(Regex("\\D"), "")
        ?.toIntOrNull() ?: 0

    /* ============================
       ACTORS
    ============================= */
    val actors = document.select("span[itemprop=actors]")
        .map { it.text().trim() }

    /* ============================
       RECOMMENDATIONS
    ============================= */
    val recommendations = document.select("#single_relacionados article")
        .mapNotNull { it.toRecommendResult() }


    /* =============================================================
       IF MOVIE → RETURN MOVIE RESPONSE
    ============================================================= */
    val isSeries = document.selectFirst("#episodes") != null

    if (!isSeries) {
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            addScore(rating)
            addActors(actors)
            this.duration = duration
            this.recommendations = recommendations
        }
    }


    /* =============================================================
       SERIES — EPISODE PARSER
    ============================================================= */

    val episodes = document.select("#episodes #serie_contenido")
        .flatMap { seasonBlock ->
            val seasonNumber = seasonBlock.selectFirst("h3")?.text()
                ?.filter { it.isDigit() }
                ?.toIntOrNull()

            seasonBlock.select("a").map { a ->
                val href = fixUrl(a.attr("href"))
                val epText = a.text().trim()

                // Episode number extraction
                val episodeNum = epText
                    .substringAfter("EP")
                    .takeWhile { it.isDigit() }
                    .toIntOrNull()

                newEpisode(href) {
                    this.name = epText
                    this.season = seasonNumber
                    this.episode = episodeNum
                }
            }
        }

    return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
        this.posterUrl = poster
        this.year = year
        this.plot = description
        this.tags = tags
        addScore(rating)
        addActors(actors)
        this.duration = duration
        this.recommendations = recommendations
    }
}



    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {

    val document = app.get(data).document

   
    val servers = document.select("li.dooplay_player_option")
        .mapNotNull { it.attr("data-url") }
        .filter { it.isNotBlank() }

    
    val iframeDefault = document.selectFirst("iframe")?.attr("src")
    if (!iframeDefault.isNullOrBlank()) {
        loadExtractor(iframeDefault, data, subtitleCallback, callback)
    }

    
    servers.apmap { serverUrl ->
        val fixed = fixUrl(serverUrl)
        loadExtractor(fixed, data, subtitleCallback, callback)
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

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src").takeIf { it?.isNotEmpty() == true }
                ?: this?.attr("src")
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}
