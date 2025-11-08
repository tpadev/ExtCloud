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

    override var mainUrl = "https://filmapik.singles"
    private var directUrl: String? = null
    override var name = "Filmapik"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
            setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
            mainPageOf(
                    "tvshows/page/%d/" to "Serial Terbaru",
                    "latest/page/%d/" to "Film Terbaru",
                    "category/box-office/page/%d/" to "Box Office",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("div.items.normal article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
    val a = this.selectFirst("a[href][title]") ?: return null
    val title = a.attr("title").trim()
    val href = fixUrl(a.attr("href"))

    val posterUrl = fixUrlNull(this.selectFirst("img[src]")?.attr("src")).fixImageQuality()

    // Ambil rating (angka seperti 5.3)
    val ratingText = this.selectFirst("div.rating")?.ownText()?.trim()
    val score = ratingText?.toDoubleOrNull()?.let { Score.from10(it) }

    // Ambil kualitas video (WEBDL, WEBRip, BluRay, dll)
    val quality = this.selectFirst("span.quality")?.text()?.trim()

    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
        if (quality != null && quality.isNotEmpty()) addQuality(quality)
        this.score = Score.from10(ratingText?.toDoubleOrNull())
    }
}


    override suspend fun search(query: String): List<SearchResponse> {
        val document =
                app.get("${mainUrl}?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L)
                        .document
        val results = document.select("article.item").mapNotNull { it.toSearchResult() }
        return results
    }

    private fun Element.toRecommendResult(): SearchResponse? {
    val a = this.selectFirst("a[href]") ?: return null
    val href = fixUrl(a.attr("href"))

    val img = a.selectFirst("img[src][alt]") ?: return null
    val title = img.attr("alt").trim()
    val posterUrl = fixUrlNull(img.attr("src")).fixImageQuality()

    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
    }
}

    override suspend fun load(url: String): LoadResponse {
    val fetch = app.get(url)
    val document = fetch.document

    // Judul
    val title = document.selectFirst("h1[itemprop=name]")?.text()
        ?.replace("Nonton", "")
        ?.replace("Sub Indo Filmapik", "")
        ?.replace("Subtitle Indonesia Filmapik", "")
        ?.trim() ?: return newMovieLoadResponse("", url, TvType.Movie, url)

    // Poster utama
    val poster = document.selectFirst("div.poster img")?.attr("src")?.let { fixUrl(it) }

    // Genre
    val tags = document.select("span.sgeneros a").map { it.text() }

    // Pemeran dan info lain
    val stars = document.select("span.tagline:contains(Stars:) a").map { it.text() }
    val network = document.select("span.tagline:contains(Networks:) a").text()
    val seriesStatus = document.select("div.info-more:contains(Series Status:)").text()
    val year = Regex("(19|20)\\d{2}").find(title)?.value?.toIntOrNull()

    // Rating & Deskripsi
    val rating = document.selectFirst("div.sbox b:contains(IMDb)")?.nextElementSibling()?.text()?.toDoubleOrNull()
    val description = document.selectFirst("div#description, div[itemprop=description]")?.text()?.trim()

    // Rekomendasi
    val recommendations = document.select("#single_relacionados article").mapNotNull { it.toRecommendResult() }

    // Cek apakah series
    val isSeries = url.contains("/tvshows/") || document.select("div#episodes").isNotEmpty()

    if (isSeries) {
        // Ambil episode list (kalau ada)
        val episodes = document.select("div#episodes a").mapIndexed { index, ep ->
            val href = fixUrl(ep.attr("href"))
            val name = ep.text().ifBlank { "Episode ${index + 1}" }
            newEpisode(href) {
                this.name = name
                this.episode = index + 1
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.tags = tags
            addActors(stars)
            this.plot = description
            addScore(rating)
            this.recommendations = recommendations
            this.showStatus = seriesStatus.ifBlank { null }
            this.network = network.ifBlank { null }
        }
    } else {
        // Movie
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.tags = tags
            addActors(stars)
            this.plot = description
            addScore(rating)
            this.recommendations = recommendations
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

    // 🎥 Ambil iframe dari player server
    document.select("div.cframe iframe, iframe.metaframe").forEach { iframe ->
        val src = iframe.attr("src")
        if (src.isNotBlank()) {
            loadExtractor(httpsify(src), data, subtitleCallback, callback)
        }
    }

    // 💾 Ambil semua link download (seperti Filemoon, Buzzheavier, dll)
    document.select("div.links_table a.myButton").forEach { linkEl ->
        val downloadUrl = linkEl.attr("href")
        if (downloadUrl.isNotBlank()) {
            loadExtractor(downloadUrl, data, subtitleCallback, callback)
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
