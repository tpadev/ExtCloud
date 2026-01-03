package com.kitanonton

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

class KitaNonton : MainAPI() {

    override var mainUrl = "http://nontonfilm.gratis"
    override var name = "KitaNonton"
    override var lang = "id"
    override val hasMainPage = true

    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "/" to "Terbaru",
        "/best-rating/" to "Best Rating",
        "/tv-series/" to "Tv Series",
        "/genre/action/" to "Action",
        "/genre/crime/" to "Crime",
        "/genre/adventure/" to "Adventure",
        "/genre/horror/" to "Horror",
        "/country/thailand/" to "Thailand",
        "/country/korea/" to "Korea",
        "/country/philippines/" to "Philipines",
        "/country/japan/" to "Jepan"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data == "/") mainUrl else "${mainUrl}${request.data}page/$page/"
        val document = app.get(url).document

        val items = document.select("div.slider-item, article.post")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2.caption, h2 > a")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        val poster = selectFirst("img")?.getImageAttr()

        val ratingText = selectFirst("div.rating")?.ownText()?.trim()

        return newMovieSearchResponse(title, fixUrl(href), TvType.Movie) {
            posterUrl = fixUrlNull(poster)
            if (!ratingText.isNullOrEmpty()) addScore(ratingText)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.post, div.slider-item")
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1, h2[itemprop=name]")?.text()?.trim() ?: "Unknown"
        val poster = document.selectFirst("div.content-poster img")?.getImageAttr()
        val description = document.selectFirst("div[itemprop=description] p")?.text()
        val year = document.select("a[href*='/year/']").firstOrNull()?.text()?.toIntOrNull()
        val tags = document.select("a[href*='/genre/']").eachText()
        val ratingText = document.selectFirst("span[itemprop=ratingValue]")?.text()?.trim()
        val trailer = document.selectFirst("a.fancybox[href*='youtube']")?.attr("href")
        val actors = document.select("span[itemprop=actors] a").map { it.text() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            posterUrl = fixUrlNull(poster)
            plot = description
            this.year = year
            this.tags = tags
            if (!ratingText.isNullOrEmpty()) addScore(ratingText)
            addActors(actors)
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
        val postId = document.selectFirst("div#muvipro_player_content_id")?.attr("data-id") ?: return false
        val baseUrl = getBaseUrl(data)

        document.select("div.tab-content-ajax").forEach { tab ->
            val server = app.post(
                "$baseUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "muvipro_player_content",
                    "tab" to tab.attr("id"),
                    "post_id" to postId
                )
            ).document.selectFirst("iframe")?.getIframeAttr()?.let { httpsify(it) } ?: return@forEach

            loadExtractor(server, baseUrl, subtitleCallback, callback)
        }

        return true
    }

    private fun Element.getImageAttr(): String =
        when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }

    private fun Element?.getIframeAttr(): String? =
        this?.attr("data-litespeed-src")?.takeIf { it.isNotEmpty() } ?: this?.attr("src")

    private fun getBaseUrl(url: String): String =
        URI(url).let { "${it.scheme}://${it.host}" }
}
