package com.layarkaca

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class LayarKaca : MainAPI() {
    override var mainUrl = "https://tv.lk21official.love"
    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/%d/" to "Film Terpopuler",
        "$mainUrl/most-commented/page/%d/" to "Film Komentar Terbanyak",
        "$mainUrl/rating/page/%d/" to "Film IMDb Rating",
        "$mainUrl/latest/page/%d/" to "Film Terbaru",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data.format(page)).document
        val movies = doc.select("div.gallery-grid article")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, movies)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h3.poster-title")?.text() ?: return null
        val href = fixUrl(selectFirst("a")?.attr("href") ?: return null)
        val poster = selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("div.gallery-grid article").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: "No Title"
        val poster = doc.selectFirst("div.poster img")?.attr("src")
        val year = doc.selectFirst("span.year")?.text()?.toIntOrNull()
        val plot = doc.selectFirst("div.entry-content p")?.text()
        val genres = doc.select("div.gmr-moviedata a").map { it.text() }
        val trailer = doc.selectFirst("iframe[src*=\"youtube\"]")?.attr("src")

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
            this.tags = genres
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // Ambil iframe player streaming
        val iframes = doc.select("iframe[src], iframe[data-src]")
        iframes.forEach { iframe ->
            val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
            if (src.isNotBlank()) {
                loadExtractor(fixUrl(src), data, subtitleCallback, callback)
            }
        }

        // Ambil link unduh langsung (mp4/mkv)
        val downloads = doc.select("a[href*=\".mp4\"], a[href*=\".mkv\"]")
        downloads.forEach {
            val link = fixUrl(it.attr("href"))
            val name = it.text().ifBlank { "Mirror" }
            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = "Unduh - $name",
                    url = link,
                    referer = mainUrl,
                    quality = getQualityFromName(name),
                    type = INFER_TYPE
                )
            )
        }

        return true
    }
}