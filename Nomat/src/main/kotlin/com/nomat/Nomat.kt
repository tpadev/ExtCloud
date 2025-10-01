package com.nomat

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
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Score

class Nomat : MainAPI() {

    override var mainUrl = "https://nomat.site"
    private var directUrl: String? = null
    override var name = "DutaMovie"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
            setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
            mainPageOf(
                    "slug/film-terbaru/page/%d/" to "Film Terbaru",
                    "slug/film-terfavorit/page/%d/" to "Film Terfavorit",
                    "slug/film-box-office/page/%d/" to "Film Box Office",
            )

   override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val data = request.data.format(page)
    val document = app.get("$mainUrl/$data").document
    val home = document.select("div.item").mapNotNull { it.toSearchResult() }
    return newHomePageResponse(request.name, home)
}

    private fun Element.toSearchResult(): SearchResponse? {
    val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
    val title = this.selectFirst("div.title")?.text()?.trim() ?: return null

    // Ambil poster dari CSS background
    val style = this.selectFirst("div.poster")?.attr("style") ?: ""
    val posterUrl = Regex("url\\(['\"]?(.*?)['\"]?\\)").find(style)?.groupValues?.get(1)

    // Rating
    val rating = this.selectFirst("div.rtg")?.ownText()?.trim()

    // Cek apakah ini Series (ada label Eps. atau kata Season/Episode)
    val epsText = this.selectFirst("div.qual")?.text()?.trim()
    val episode = Regex("Eps.?\\s?([0-9]+)", RegexOption.IGNORE_CASE)
        .find(epsText ?: "")
        ?.groupValues?.getOrNull(1)?.toIntOrNull()

    return if (episode != null || title.contains("Season", true) || title.contains("Episode", true)) {
        // Jika series
        newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            addSub(episode)
            if (!rating.isNullOrEmpty()) this.score = Score.fromString(rating)
        }
    } else {
        // Jika movie
        newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            if (!rating.isNullOrEmpty()) this.score = Score.fromString(rating)
        }
    }
}


    override suspend fun search(query: String): List<SearchResponse> {
    val document = app.get("$mainUrl/search/$query", timeout = 50L).document
    return document.select("div.item-content").mapNotNull { it.toSearchResult() }
}

    private fun Element.toRecommendResult(): SearchResponse? {
    val href = fixUrl(this.attr("href"))
    val title = this.selectFirst("div.title")?.text()?.trim() ?: return null

    // Ambil poster dari CSS background
    val style = this.selectFirst("div.poster")?.attr("style") ?: ""
    val posterUrl = Regex("url\\(['\"]?(.*?)['\"]?\\)").find(style)?.groupValues?.get(1)

    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
    }
}


    override suspend fun load(url: String): LoadResponse {
    val fetch = app.get(url)
    val document = fetch.document

    val title =
        document.selectFirst("div.video-title h1")?.text()
            ?.substringBefore("Season")
            ?.substringBefore("Episode")
            ?.trim()
            .toString()

    val poster =
        fixUrlNull(document.selectFirst("div.video-poster")?.attr("style")
            ?.substringAfter("url('")
            ?.substringBefore("')"))?.fixImageQuality()

    val tags = document.select("div.video-genre a").map { it.text() }
    val year = document.select("div.video-duration a[href*=/category/year/]").text().toIntOrNull()
    val description = document.selectFirst("div.video-synopsis")?.text()?.trim()
    val trailer = document.selectFirst("div.video-trailer iframe")?.attr("src")
    val rating = document.selectFirst("div.video-rating")?.text()?.filter { it.isDigit() || it == '.' }

    val actors = document.select("div.video-actor a").map { it.text() }
    val recommendations = document.select("div.section .item-content").mapNotNull { it.toRecommendResult() }

    val tvType = if (url.contains("/serial-tv/") || document.select("div.video-episodes a").isNotEmpty()) TvType.TvSeries else TvType.Movie

    return if (tvType == TvType.TvSeries) {
        val episodes = document.select("div.video-episodes a").map { eps ->
            val href = fixUrl(eps.attr("href"))
            val name = eps.text() // "Eps. 1"
            val episode = Regex("\\d+").find(name)?.value?.toIntOrNull()
            val season = Regex("Season\\s?(\\d+)").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()

            newEpisode(href) {
                this.name = name
                this.episode = episode
                this.season = season
            }
        }

        newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            addScore(rating)
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
            addScore(rating)
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
    val doc = app.get(data).document

    // Ambil semua server di halaman nontonhemat.link
    doc.select("div.server-item").forEach { server ->
        val serverUrl = server.attr("data-url")
        if (serverUrl.isNullOrBlank()) return@forEach

        try {
            val embedDoc = app.get(serverUrl).document
            val iframe = embedDoc.selectFirst("iframe")?.attr("src")
            if (!iframe.isNullOrBlank()) {
                loadExtractor(iframe, data, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            logError(e)
        }
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
