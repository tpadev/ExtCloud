package com.klikxxi

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

class Klikxxi : MainAPI() {
    override var mainUrl = "https://klikxxi.fit"
    override var name = "Klikxxi"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
            setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
            mainPageOf(
                   "?s=&search=advanced&post_type=movie&index=&orderby=&genre=&movieyear=&country=&quality=&paged=%d" to "Film Terbaru",
                    "tv/page/%d/" to "Series Terbaru",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val data = if (page == 1) {
        // hapus &paged=%d supaya tidak error di page 1
        request.data.replace("&paged=%d", "")
    } else {
        request.data.format(page)
    }

    val document = app.get("$mainUrl/$data").document
    val home = document.select("article.item").mapNotNull { it.toSearchResult() }
    return newHomePageResponse(request.name, home)
}

    private fun Element.toSearchResult(): SearchResponse? {
    val title = this.selectFirst("h2.entry-title a")?.text()?.trim() ?: return null
    val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
    val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr()).fixImageQuality()
    val quality = this.select("div.gmr-quality-item").text().trim().replace("-", "")
    val type = this.selectFirst("div.gmr-posttype-item")?.text()?.trim()

    return if (type.equals("TV Show", ignoreCase = true)) {
        // Kalau ada label "TV Show" → TvSeries
        val episode = this.select("div.gmr-numbeps > span").text().toIntOrNull()

        newAnimeSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            if (episode != null) addSub(episode)
        }
    } else {
        // Default → Movie
        newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            if (quality.isNotEmpty()) addQuality(quality)
        }
    }
}


    override suspend fun search(query: String): List<SearchResponse> {
    val document = app.get("$mainUrl/?s=$query", timeout = 50L).document
    return document.select("article.item").mapNotNull { it.toSearchResult() }
}

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("a > span.idmuvi-rp-title")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr().fixImageQuality())
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title, div.mvic-desc h3")
            ?.text()
            ?.substringBefore("Season")
            ?.substringBefore("Episode")
            ?.substringBefore("(")
            ?.trim()
            .orEmpty()

        val poster = document.selectFirst("figure.pull-left img, div.gmr-movieposter img, .poster img")
            ?.fixPoster()

        val description = document.selectFirst("div[itemprop=description] > p, div.desc p.f-desc, div.entry-content > p")
            ?.text()
            ?.trim()

        val tags = document.select("div.gmr-moviedata strong:contains(Genre:) > a")
            .map { it.text() }

        val year = document.select("div.gmr-moviedata strong:contains(Year:) > a")
            .text()
            .toIntOrNull()

        val trailer = document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")?.attr("href")
        val rating = document.selectFirst("span[itemprop=ratingValue]")?.text()?.toDoubleOrNull()
        val actors = document.select("div.gmr-moviedata span[itemprop=actors] a")
            .map { it.text() }
            .takeIf { it.isNotEmpty() }
        val recommendations = document.select("div.gmr-related-post article, div.related-post article")
            .mapNotNull { it.toSearchResult() }

        // === Ambil Episodes ===
        val seasonBlocks = document.select("div.gmr-season-block")
        val allEpisodes = mutableListOf<Episode>()

        seasonBlocks.forEach { block ->
            val seasonTitle = block.selectFirst("h3.season-title")?.text()?.trim()
            val seasonNumber = Regex("(\\d+)").find(seasonTitle ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

            val eps = block.select("div.gmr-season-episodes a")
                .filter { a ->
                    val t = a.text().lowercase()
                    !t.contains("view all") && !t.contains("batch")
                }
                .mapIndexedNotNull { index, epLink ->
                    val href = epLink.attr("href").takeIf { it.isNotBlank() }?.let { fixUrl(it) }
                        ?: return@mapIndexedNotNull null
                    val name = epLink.text().trim()
                    val episodeNum = Regex("E(p|ps)?(\\d+)").find(name)?.groupValues?.getOrNull(2)?.toIntOrNull()
                        ?: (index + 1)

                    newEpisode(href) {
                        this.name = name
                        this.season = seasonNumber
                        this.episode = episodeNum
                    }
                }

            allEpisodes.addAll(eps)
        }

        val episodes = allEpisodes.sortedWith(compareBy({ it.season }, { it.episode }))
        val tvType = if (episodes.isNotEmpty()) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.year = year
                if (rating != null) addScore(rating.toString(), 10)
                addActors(actors)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.year = year
                addActors(actors)
                addTrailer(trailer)
                if (rating != null) addScore(rating.toString(), 10)
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
    val id = document.selectFirst("div#muvipro_player_content_id")?.attr("data-id")

    // 🎬 Ambil iframe player (streaming)
    if (id.isNullOrEmpty()) {
        document.select("ul.muvipro-player-tabs li a").amap { ele ->
            val iframe = app.get(fixUrl(ele.attr("href")))
                .document
                .selectFirst("div.gmr-embed-responsive iframe")
                ?.getIframeAttr()
                ?.let { httpsify(it) }
                ?: return@amap

            loadExtractor(iframe, "$directUrl/", subtitleCallback, callback)
        }
    } else {
        document.select("div.tab-content-ajax").amap { ele ->
            val server = app.post(
                "$directUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "muvipro_player_content",
                    "tab" to ele.attr("id"),
                    "post_id" to "$id"
                )
            ).document
                .select("iframe")
                .attr("src")
                .let { httpsify(it) }

            loadExtractor(server, "$directUrl/", subtitleCallback, callback)
        }
    }

document.select("ul.gmr-download-list li a").forEach { linkEl ->
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