package com.layarkaca

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class LayarKaca : MainAPI() {
    override var mainUrl = "https://tv.lk21official.love"
    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/populer" to "Film Terpopuler",
        "$mainUrl/most-commented" to "Film Dengan Komentar Terbanyak",
        "$mainUrl/rating" to "Film IMDb Rating",
        "$mainUrl/latest" to "Film Terbaru",
    )

    // --- Ambil daftar film dari main page ---
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + "/page/$page").document
        val movies = document.select("article[itemtype*=Movie]")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, movies, hasNext = movies.isNotEmpty())
    }

    // --- Fungsi konversi dari element HTML ke SearchResponse ---
    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h3.poster-title")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }
        val year = selectFirst("span.year")?.text()?.toIntOrNull()

        return newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
            this.posterUrl = poster
            this.year = year
        }
    }

    // --- Search film ---
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article[itemtype*=Movie]")
            .mapNotNull { it.toSearchResult() }
    }

    // --- Ambil detail film ---
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title, h1[itemprop=name]")
            ?.text()?.trim() ?: "Tanpa Judul"
        val poster = document.selectFirst("div.poster img, figure img")
            ?.getImageAttr()?.let { fixUrlNull(it) }
        val year = document.selectFirst("span.year")?.text()?.toIntOrNull()
        val plot = document.selectFirst("div.entry-content p, div[itemprop=description]")
            ?.text()?.trim() ?: "Plot Tidak Ditemukan"

        // link streaming / download biasanya ada di iframe atau tombol
        val link = document.selectFirst("a[href*=/stream], a[href*=lk21]")?.attr("href")

        return newMovieLoadResponse(title, url, TvType.Movie, link) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
        }
    }
}