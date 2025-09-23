package com.ngefilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URI

class Ngefilm : MainAPI() {
    override var mainUrl = "https://new18.ngefilm.site"
    private var directUrl: String? = null
    override var name = "Ngefilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Upload Terbaru",
        "$mainUrl/?s=&search=advanced&post_type=tv" to "Semua Series",
        "$mainUrl/?s=&search=advanced&post_type=tv&genre=drama&country=korea" to "Drama Korea",
        "$mainUrl/?s=&search=advanced&post_type=tv&country=indonesia" to "Series Indonesia",
        "$mainUrl/country/indonesia/" to "Film Indonesia",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val doc = app.get(request.data).document
    val homeLists = mutableListOf<HomePageList>()

    // Coba ambil highlight dari carousel paling atas
    val highlights = doc.select("div.gmr-box-content.gmr-box-archive.text-center article.has-post-thumbnail, article.has-post-thumbnail")
        .take(5)  // atur berapa banyak highlight
        .mapNotNull { it.toSearchResult() }

    if (highlights.isNotEmpty()) {
        homeLists.add(HomePageList("Highlight", highlights))
    }

    // Ambil list film utama / ‘upload terbaru’
    val items = doc.select("article.has-post-thumbnail").mapNotNull { it.toSearchResult() }
    homeLists.add(HomePageList(request.name, items))

    return newHomePageResponse(homeLists)
}

    private fun Element.toSearchResult(): SearchResponse? {
        val link = this.selectFirst("h2.entry-title a") ?: return null
        val title = link.text()?.trim() ?: return null
        val poster = fixUrlNull(this.selectFirst("img")?.getImageAttr())
        val qualityText = this.selectFirst(".gmr-quality-item, .mli-quality")?.text()?.trim()

        return newMovieSearchResponse(title, link.attr("href"), TvType.Movie) {
            this.posterUrl = poster
            this.quality = getQualityFromString(qualityText)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("article.has-post-thumbnail").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fetch = app.get(url)
        directUrl = getBaseUrl(fetch.url)
        val doc = fetch.document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(doc.selectFirst(".thumb img")?.getImageAttr())
        val rawPlot = doc.selectFirst("div.entry-content.entry-content-single p")?.text()?.trim()
        val plot = rawPlot?.substringBefore("Oleh:")?.trim()
        val year = doc.selectFirst("span[itemprop=dateCreated]")?.text()?.toIntOrNull()
        val type = if (doc.select("div.gmr-listseries a").isNotEmpty()) TvType.TvSeries else TvType.Movie
        val trailer = doc.selectFirst("a.gmr-trailer-popup")?.attr("href")
            ?: doc.selectFirst("div.gmr-embed-responsive iframe")?.attr("src")

        // ✅ Rekomendasi (Film Terkait)
        val recommendations = doc.select("div.gmr-related-posts article").mapNotNull { rec ->
            val recTitle = rec.selectFirst("h2.entry-title a")?.text()?.trim() ?: return@mapNotNull null
            val recUrl = fixUrl(rec.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
            val recPoster = fixUrlNull(rec.selectFirst("img")?.getImageAttr())
            newMovieSearchResponse(recTitle, recUrl, TvType.Movie) {
                this.posterUrl = recPoster
            }
        }

        return when (type) {
            TvType.Movie -> {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = plot
                    addTrailer(trailer)
                    this.recommendations = recommendations
                }
            }
            TvType.TvSeries -> {
                val episodes = doc.select("div.gmr-listseries a")
                    .filter { !it.text().contains("Pilih", ignoreCase = true) }
                    .mapIndexed { idx, el ->
                        newEpisode(fixUrl(el.attr("href"))) {
                            this.name = el.text().ifBlank { "Episode ${idx + 1}" }
                            this.season = null
                            this.episode = idx + 1
this.posterUrl = poster
                        }
                    }

                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = plot
                    this.posterUrl = poster
                    this.recommendations = recommendations
                }
            }
            else -> null
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

        if (id.isNullOrEmpty()) {
            document.select("ul.muvipro-player-tabs li a").apmap { ele ->
                val iframe = app.get(fixUrl(ele.attr("href")))
                    .document.selectFirst("div.gmr-embed-responsive iframe")
                    ?.getIframeAttr()?.let { httpsify(it) } ?: return@apmap

                loadExtractor(iframe, "$directUrl/", subtitleCallback, callback)
            }
        } else {
            document.select("div.tab-content-ajax").apmap { ele ->
                val server = app.post(
                    "$directUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to ele.attr("id"),
                        "post_id" to "$id"
                    )
                ).document.select("iframe").attr("src").let { httpsify(it) }

                loadExtractor(server, "$directUrl/", subtitleCallback, callback)
            }
        }
        return true
    }

    // Helpers
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

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}