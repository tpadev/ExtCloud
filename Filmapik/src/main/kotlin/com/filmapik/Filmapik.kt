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
import com.lagradost.cloudstream3.toNewSearchResponseList
import java.net.URI
import org.jsoup.nodes.Element

class Filmapik : MainAPI() {

    override var mainUrl = "https://filmapik.golf"
    private var directUrl: String? = null
    override var name = "Filmapik🍠"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
            setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
    mainPageOf(
        "/category/box-office/page/%d/" to "Box Office",
        "/tvshows/page/%d/" to "Tv Show",
        "/latest/page/%d/" to "Film Terbaru",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val data = request.data.format(page)
    val document = app.get("$mainUrl/$data").document
    val home = document.select("article.item").mapNotNull { it.toSearchResult() }
    return newHomePageResponse(request.name, home)
}

private fun Element.toSearchResult(): SearchResponse? {

    val title = this.selectFirst("div.data > h3")?.text()?.trim() ?: return null
    val href = fixUrl(this.selectFirst("a.see.play3")!!.attr("href"))
    val posterUrl = fixUrlNull(this.selectFirst("div.poster img")?.getImageAttr())
        .fixImageQuality()

    val quality = this.selectFirst("span.quality")?.text()?.trim() ?: ""

    val ratingText = this.selectFirst("div.rating")?.ownText()?.trim()
        ?.replace("*", "") // hilangkan asterisk

    return if (quality.isEmpty()) {

        newAnimeSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.score = Score.from10(ratingText?.toDoubleOrNull())
        }

    } else {

        newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(quality)
            this.score = Score.from10(ratingText?.toDoubleOrNull())
        }
    }
}

    override suspend fun search(query: String, page: Int): SearchResponseList? {
    val url = if (page == 1) {
        "$mainUrl/?s=$query"
    } else {
        "$mainUrl/?s=$query&paged=$page"
    }

    val document = app.get(url, timeout = 50L).document

    val results = document.select("div.search-result-item article, article")
        .mapNotNull { it.toSearchResult() }
        .toNewSearchResponseList()

    return results
}

        private fun Element.toRecommendResult(): SearchResponse? {

    // Title
    val title = this.selectFirst("div.relacionados-title")
        ?.text()
        ?.trim()
        ?: return null

    // URL
    val href = fixUrl(this.selectFirst("a")!!.attr("href"))

    // Poster
    val posterUrl = fixUrlNull(
        this.selectFirst("a img")?.getImageAttr()
    ).fixImageQuality()

    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
    }
}


    override suspend fun load(url: String): LoadResponse {
    val fetch = app.get(url)
    directUrl = getBaseUrl(fetch.url)
    val document = fetch.document

    // ---------- TITLE ----------
    val title = document.selectFirst("h1[itemprop=name]")
        ?.text()
        ?.trim()
        ?: "Unknown Title"

    // ---------- POSTER ----------
    val poster =
    document.selectFirst("div.poster img")
        ?.getImageAttr()
        ?.let { fixUrlNull(it).fixImageQuality() }


    // ---------- GENRE ----------
    val tags = document.select("span.generos a").map { it.text() }

    // ---------- YEAR ----------
    val year = document.selectFirst("span[itemprop=dateCreated]")
        ?.text()
        ?.trim()
        ?.toIntOrNull()

    // ---------- QUALITY ----------
    val quality = document.selectFirst("span.quality")?.text()?.trim()

    // ---------- COUNTRY ----------
    val country = document.selectFirst("span[itemprop=country]")
        ?.text()
        ?.trim()

    // ---------- DIRECTOR ----------
    val directors = document.select("span[itemprop=director]").map { it.text() }

    // ---------- ACTORS ----------
    val actors = document.select("span[itemprop=actor]").map { it.text() }

    // ---------- DESCRIPTION ----------
    val description = document.select("div.sbox p").joinToString("\n") {
        it.text().trim()
    }

    // ---------- IMDb RATING ----------
    val rating = document.selectFirst("span.imdb")
        ?.text()
        ?.replace("IMDb", "")
        ?.trim()

    // ---------- RECOMMENDATIONS ----------
    val recommendations = document.select("div.relacionados_item")
        .mapNotNull { rec ->
            val rTitle = rec.selectFirst("div.relacionados-title")?.text()?.trim() ?: return@mapNotNull null
            val rHref = fixUrl(rec.selectFirst("a")!!.attr("href"))
            val rPoster = fixUrlNull(rec.selectFirst("a img")?.getImageAttr())
                ?.fixImageQuality()

            newMovieSearchResponse(rTitle, rHref, TvType.Movie) {
                this.posterUrl = rPoster
            }
        }

    // ---------- DETECT TV SERIES OR MOVIE ----------
    val isSeries = url.contains("/tvshows/") || document.select("#seasons").isNotEmpty()

    // =====================================================================
    // =========================== TV SERIES ===============================
    // =====================================================================
    if (isSeries) {

        val episodeList = mutableListOf<Episode>()

        val seasonBlocks = document.select("#seasons .se-c")
        for (i in seasonBlocks.indices) {

            val seasonNumber =
                seasonBlocks[i].selectFirst("span.set")?.text()?.toIntOrNull() ?: (i + 1)

            val episodeBlock = seasonBlocks[i].nextElementSibling() // <div class="se-a">

            val eps = episodeBlock.select("ul.episodios li a")

            eps.forEach { ep ->

                val name = ep.text().trim()    // EP1-2
                val href = fixUrl(ep.attr("href"))

                val episodeNumber =
                    name.filter { it.isDigit() }.toIntOrNull() // Ambil nomor terbesar

                episodeList.add(
                    newEpisode(href) {
                        this.name = name
                        this.season = seasonNumber
                        this.episode = episodeNumber
                    }
                )
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            addActors(actors)
            addScore(rating)
            this.recommendations = recommendations
        }
    }

    // =====================================================================
    // ============================= MOVIE =================================
    // =====================================================================
    return newMovieLoadResponse(title, url, TvType.Movie, url) {
        this.posterUrl = poster
        this.year = year
        this.plot = description
        this.tags = tags
        addScore(rating)
        addActors(actors)
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

    // 1. Ambil iframe utama (metaframe)
    val primaryIframe = document.selectFirst("iframe[src*=\"http\"], iframe.metaframe, iframe.rpts")
        ?.attr("src")
        ?.let { fixUrl(it) }

    if (primaryIframe != null) {
        loadExtractor(primaryIframe, "$directUrl/", subtitleCallback, callback)
    }

    // 2. Ambil server lain, seperti STREAMCAST / VIP SERVER / HYDRAX
    val otherFrames = document.select("iframe")

    otherFrames.forEach { frame ->
        val src = frame.attr("src")

        if (!src.isNullOrBlank() && src.startsWith("http")) {
            val fixed = fixUrl(src)
            loadExtractor(fixed, "$directUrl/", subtitleCallback, callback)
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
