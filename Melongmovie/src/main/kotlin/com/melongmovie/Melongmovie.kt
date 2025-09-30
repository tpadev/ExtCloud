package com.melongmovie

import com.lagradost.cloudstream3.*  
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors  
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore  
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer  
import com.lagradost.cloudstream3.MainAPI  
import com.lagradost.cloudstream3.SearchResponse  
import com.lagradost.cloudstream3.TvType  
import com.lagradost.cloudstream3.mainPageOf  
import com.lagradost.cloudstream3.newMovieSearchResponse  
import com.lagradost.cloudstream3.newTvSeriesLoadResponse  
import com.lagradost.cloudstream3.newMovieLoadResponse  
import com.lagradost.cloudstream3.newEpisode  
import com.lagradost.cloudstream3.utils.*  
import org.jsoup.nodes.Element  

class Oppadrama : MainAPI() {
    override var mainUrl = "https://tv11.melongmovies.com"
    override var name = "Melongmovie"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/latest-movies/page/%d/" to "Movie Terbaru",
        "$mainUrl/series/page/%d/" to "Update Series",
        "$mainUrl/country/usa/page/%d/" to "Film Barat",
        "$mainUrl/country/south-korea/page/%d/" to "Film Korea",
        "$mainUrl/country/thailand/page/%d/" to "Film Thailand",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val url = "${request.data}page/$page/"   // karena struktur pakai /page/{n}/
    val document = app.get(url).document
    val items = document.select("div.los article.box")
        .mapNotNull { it.toSearchResult() }
    return newHomePageResponse(HomePageList(request.name, items), hasNext = items.isNotEmpty())
}

private fun Element.toSearchResult(): SearchResponse? {
    val linkElement = this.selectFirst("a.tip") ?: return null
    val href = fixUrl(linkElement.attr("href"))

    val title = this.selectFirst("h2.entry-title")?.text()?.trim()
        ?: linkElement.attr("title")
        ?: this.selectFirst("img")?.attr("title")
        ?: return null

    val poster = fixUrlNull(this.selectFirst("img")?.getImageAttr())

    // Ambil kualitas video (WEB-DL, BLURAY, dll)
    val quality = this.selectFirst("span.quality")?.text()?.trim()

    // Beda antara Movie dan TV Series bisa dideteksi dari itemtype
    val itemType = this.attr("itemtype") // http://schema.org/Movie atau http://schema.org/TVSeries
    val isSeries = itemType.contains("TVSeries", true)

    return if (isSeries) {
        newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = poster
            if (!quality.isNullOrBlank()) addQuality(quality)
        }
    } else {
        newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            if (!quality.isNullOrBlank()) addQuality(quality)
        }
    }
}


override suspend fun search(query: String): List<SearchResponse> {
    val document = app.get("$mainUrl/?s=$query", timeout = 50L).document
    return document.select("div.los article.box")
        .mapNotNull { it.toSearchResult() }
}

private fun Element.toRecommendResult(): SearchResponse? {
    val linkElement = this.selectFirst("a.tip") ?: return null
    val href = fixUrl(linkElement.attr("href"))

    val title = this.selectFirst("h2.entry-title")?.text()?.trim()
        ?: linkElement.attr("title")
        ?: this.selectFirst("img")?.attr("title")
        ?: return null

    val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())
    val quality = this.selectFirst("span.quality")?.text()?.trim()

    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
        if (!quality.isNullOrBlank()) addQuality(quality)
    }
}

override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document

    // Judul
    val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()

    // Poster
    val poster = document.selectFirst("div.bigcontent img")?.getImageAttr()?.let { fixUrlNull(it) }

    // Sinopsis
    val description = document.select("div.entry-content p")
        .joinToString("\n") { it.text() }
        .trim()

    // Tahun rilis
    val year = document.selectFirst("li:has(b:matchesOwn(Release)) time")?.text()
        ?.take(4)?.toIntOrNull()

    // Genre
    val tags = document.select("li:has(b:matchesOwn(Genre)) a").map { it.text() }

    // Aktor
    val actors = document.select("li:has(b:matchesOwn(Stars)) a").map { it.text() }

    // Negara
    val country = document.select("li:has(b:matchesOwn(Country)) a").map { it.text() }

    // Durasi
    val duration = document.select("li:has(b:matchesOwn(Duration)) span").text().trim()

    // Quality
    val quality = document.selectFirst("li:has(b:matchesOwn(Quality))")?.text()
        ?.replace("Quality:", "")?.trim()

    // Trailer
    val trailer = document.selectFirst("div.bixbox.trailer iframe")?.attr("src")

    // Rekomendasi
    val recommendations = document.select("div.listupd article.box")
        .mapNotNull { it.toRecommendResult() }

    // =========================
    //  Cek: Series atau Movie
    // =========================
    // Episodes (khusus untuk TV series)
val episodes = mutableListOf<Episode>()
var epIndex = 1
var currentEpName: String? = null

document.select("div.entry-content").children().forEach { el: org.jsoup.nodes.Element ->
    if (el.tagName() == "b" && el.text().contains("EPISODE", true)) {
        currentEpName = el.text().trim()
        episodes.add(newEpisode("$url#ep$epIndex") {
            this.name = currentEpName
            this.episode = epIndex
        })
        epIndex++
    }
}


    return if (episodes.isNotEmpty()) {
        // TV Series
        newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    } else {
        // Movie → ambil iframe tunggal
        val iframe = document.selectFirst("div#embed_holder iframe")?.attr("src") ?: url

        newMovieLoadResponse(title, url, TvType.Movie, iframe) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
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
    val parts = data.split("#")
    val url = parts[0]
    val epTag = parts.getOrNull(1) // contoh "ep1", null = movie

    val document = app.get(url).document
    var found = false

    if (epTag == null) {
        // ======================
        // MOVIE MODE
        // ======================
        // Player utama
        val mainIframe = document.selectFirst("div#embed_holder iframe")?.attr("src")
        if (mainIframe != null) {
            loadExtractor(fixUrl(mainIframe), url, subtitleCallback, callback)
            found = true
        }

        // Mirror host
        document.select("div.liserver a").forEach { a ->
            val mirrorUrl = a.attr("href")
            if (mirrorUrl.isNotBlank()) {
                loadExtractor(fixUrl(mirrorUrl), url, subtitleCallback, callback)
                found = true
            }
        }
    } else {
        // ======================
        // SERIES MODE
        // ======================
        var currentEpIndex = 0

        document.select("div.entry-content").children().forEach { el: org.jsoup.nodes.Element ->
            if (el.tagName() == "b" && el.text().contains("EPISODE", true)) {
                currentEpIndex++
            }
            if (el.tagName() == "iframe" && epTag == "ep$currentEpIndex") {
                val iframeUrl = fixUrl(el.attr("src"))
                loadExtractor(iframeUrl, url, subtitleCallback, callback)
                found = true
            }
        }
    }

    return found
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
}
