package com.layarkaca

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainAPI
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
        "$mainUrl/most-commented/page/%d/" to "Film Dengan Komentar Terbanyak",
        "$mainUrl/rating/page/%d/" to "Film IMDb Rating",
        "$mainUrl/latest/page/%d/" to "Film Terbaru",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data.format(page)).document

        val home = document.select("article.ml-item, div.ml-item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = selectFirst("a[href]") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val title = linkElement.attr("title")?.trim().ifBlank { null } ?: return null
        val poster = selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }
        val quality = selectFirst("span.mli-quality")?.text()?.trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            if (!quality.isNullOrBlank()) addQuality(quality)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.ml-item, div.ml-item")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        val poster = document.selectFirst("div.thumb img")?.attr("src")?.let { fixUrlNull(it) }
        val description = document.selectFirst("div[itemprop=description]")?.text()?.trim()
        val year = document.select("span[itemprop=dateCreated]").text().toIntOrNull()
        val trailer = document.selectFirst("a.trailer")?.attr("href")

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val iframes = document.select("iframe[src], iframe[data-src]")
        iframes.forEach { frame ->
            val src = listOf("data-src", "src")
                .firstNotNullOfOrNull { key -> frame.attr(key).takeIf { it.isNotBlank() } }
                ?: return@forEach

            val link = fixUrl(src)
            loadExtractor(link, data, subtitleCallback, callback)
        }
        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }
    }
}