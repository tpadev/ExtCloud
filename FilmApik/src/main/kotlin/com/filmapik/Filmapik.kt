package com.filmapik

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Filmapik : MainAPI() {
    override var mainUrl = "https://filmapik.singles"
    override var name = "FilmApik🎃"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "category/box-office/page/%d/" to "Box Office",
        "tvshows/page/%d/" to "Serial Terbaru",
        "latest/page/%d/" to "Film Terbaru",
        "category/action/page/%d/" to "Action",
        "category/romance/page/%d/" to "Romance"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/${request.data.format(page)}"
        val document = app.get(url).document
        val items = document.select("div.items.normal article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a[title][href]") ?: return null
        val title = a.attr("title").trim()
        val href = fixUrl(a.attr("href"))
        val poster = fixUrlNull(selectFirst("img[src]")?.attr("src")).fixImageQuality()
        val rating = selectFirst("div.rating")?.ownText()?.trim()?.toDoubleOrNull()
        val quality = selectFirst("span.quality")?.text()?.trim()
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
            if (!quality.isNullOrBlank()) addQuality(quality)
            this.score = Score.from10(rating)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl?s=$query&post_type[]=post&post_type[]=tv").document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val a = selectFirst("a[href]") ?: return null
        val img = a.selectFirst("img[src][alt]") ?: return null
        val href = fixUrl(a.attr("href"))
        val title = img.attr("alt").trim()
        val poster = fixUrlNull(img.attr("src")).fixImageQuality()
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
    }

    override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document
    val title = document.selectFirst(
    "h1[itemprop=name], .sheader h1, .sheader h2"
)?.text()
    ?.replace(Regex("(?i)^nonton\\s+film\\s+"), "")
    ?.replace(Regex("(?i)subtitle\\s+indonesia.*$"), "")
    ?.trim()
    ?: document.selectFirst("#info h2")?.text()
        ?.replace(Regex("(?i)^nonton\\s+film\\s+"), "")
        ?.replace(Regex("(?i)subtitle\\s+indonesia.*$"), "")
        ?.trim()
    ?: ""
    val poster = document.selectFirst(".sheader .poster img")
        ?.attr("src")
        ?.let { fixUrl(it) }
    val genres = document.select("#info .info-more span.sgeneros a")
        .map { it.text() }
    val actors = document.select(
    "#info .info-more span.tagline:contains(Actors) a"
).map { it.text() }
    val description = document.selectFirst(
        "div[itemprop=description], .wp-content, .entry-content, .desc, .entry"
    )?.text()?.trim()
        ?: "Tidak ada deskripsi."
    val year = document.selectFirst("#info .info-more .country a")
        ?.text()
        ?.toIntOrNull()
    val rating = document.selectFirst("#repimdb strong")
        ?.text()
        ?.toFloatOrNull()
    val recommendations = document
        .select("#single_relacionados article")
        .mapNotNull { it.toRecommendResult() }

    val seasonBlocks = document.select("#seasons .se-c")

    if (seasonBlocks.isNotEmpty()) {
        val episodes = mutableListOf<Episode>()

        seasonBlocks.forEach { block ->
            val seasonNum = block
                .selectFirst(".se-q .se-t")
                ?.text()
                ?.filter { it.isDigit() }
                ?.toIntOrNull()
                ?: 1

            block.select(".se-a ul.episodios li a")
                .forEachIndexed { index, ep ->

                    val epUrl = fixUrl(ep.attr("href"))
                    val epName = ep.text().ifBlank {
                        "Episode ${index + 1}"
                    }

                    episodes.add(
                        newEpisode(epUrl) {
                            this.name = epName
                            this.season = seasonNum
                            this.episode = index + 1
                        }
                    )
                }
        }

        return newTvSeriesLoadResponse(
            title,
            url,
            TvType.TvSeries,
            episodes
        ) {
            this.posterUrl = poster
            this.year = year
            this.tags = genres
            addActors(actors)
            this.plot = description
            this.recommendations = recommendations
            this.score = Score.from10(rating)
        }
    }

    val playUrl = document
        .selectFirst("#clickfakeplayer, .fakeplayer a")
        ?.attr("href")
        ?.let { fixUrl(it) }

    return newMovieLoadResponse(
    title,
    playUrl ?: url,
    TvType.Movie,
    playUrl ?: url   
) {
    this.posterUrl = poster
    this.year = year
    this.tags = genres
    addActors(actors)
    this.plot = description
    this.recommendations = recommendations
    this.score = Score.from10(rating)
}
}


    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {

    val doc = app.get(data).document

  
    doc.select("div.pframe iframe[src]").forEach { iframe ->
        val iframeUrl = fixUrl(iframe.attr("src"))
        loadExtractor(iframeUrl, data, subtitleCallback, callback)
    }

    
    doc.select("li.dooplay_player_option[data-url]").forEach { el ->
        val serverUrl = el.attr("data-url").trim()
        if (serverUrl.isNotEmpty()) {
            loadExtractor(serverUrl, data, subtitleCallback, callback)
        }
    }

    return true
}


    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val match = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.firstOrNull()
        return if (match != null) this.replace(match, "") else this
    }

    private fun fixUrl(url: String): String {
        return if (url.startsWith("http")) url else "$mainUrl$url"
    }

    private fun fixUrlNull(url: String?): String? {
        return url?.let { fixUrl(it) }
    }
}