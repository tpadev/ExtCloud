package com.samehadaku

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element

class Samehadaku : MainAPI() {

    override var mainUrl = "https://v1.samehadaku.how"
    override var name = "Samehadaku⛩️"
    override var lang = "id"
    override val hasMainPage = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Episode Terbaru",
        "daftar-anime-2/?title=&status=&type=TV&order=popular&page=" to "TV Populer",
        "daftar-anime-2/?title=&status=&type=OVA&order=title&page=" to "OVA",
        "daftar-anime-2/?title=&status=&type=Movie&order=title&page=" to "Movie"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<HomePageList>()

        if (request.name != "Episode Terbaru" && page <= 1) {
            val doc = app.get(request.data).document
            doc.select("div.widget_senction:not(:contains(Baca Komik))").forEach { block ->
                val header = block.selectFirst("div.widget-title h3")?.ownText() ?: return@forEach
                val home = block.select("div.animepost").mapNotNull { it.toSearchResult() }
                if (home.isNotEmpty()) items.add(HomePageList(header, home))
            }
        }

        if (request.name == "Episode Terbaru") {
            val document = app.get("${request.data}$page").document
            val home = document.select("div.post-show ul li").mapNotNull { li ->
                val a = li.selectFirst("a") ?: return@mapNotNull null
                val rawTitle = a.attr("title").ifBlank { a.text() }
                val ep = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(rawTitle)
                    ?.groupValues
                    ?.getOrNull(1)
                val title = rawTitle
                    .replace(Regex("Episode\\s*\\d+", RegexOption.IGNORE_CASE), "")
                    .removeBloat()
                    .trim()
                val epText = ep?.let { "Ep $it" } ?: ""
                val href = fixUrl(a.attr("href"))
                val poster = fixUrlNull(li.selectFirst("img")?.attr("src"))

                newAnimeSearchResponse("$title $epText", href, TvType.Anime) {
                    posterUrl = poster
                    addDubStatus(DubStatus.Subbed)
                }
            }
            items.add(HomePageList(request.name, home, true))
        }

        return newHomePageResponse(items)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val title = this.selectFirst("div.title, h2.entry-title a, div.lftinfo h2")?.text()?.trim()
            ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("main#main div.animepost").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fixUrl = if (url.contains("/anime/")) {
            url
        } else {
            app.get(url).document.selectFirst("div.nvs.nvsc a")?.attr("href")
        }
        val document = app.get(fixUrl ?: return null).document
        val title = document.selectFirst("h1.entry-title")?.text()?.removeBloat() ?: return null
        val poster = document.selectFirst("div.thumb > img")?.attr("src")
        val description = document.select("div.desc p").text().trim()
        val tags = document.select("div.genre-info > a").map { it.text() }
        val trailer = document.selectFirst("div.trailer-anime iframe")?.attr("src")
        val year = document.selectFirst("div.spe > span:contains(Rilis)")?.ownText()
            ?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }
        val status = when (
            document.selectFirst("div.spe > span:contains(Status)")?.ownText()
        ) {
            "Ongoing" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
        val type = when {
            url.contains("/ova/", true) -> TvType.OVA
            url.contains("/movie/", true) -> TvType.AnimeMovie
            else -> TvType.Anime
        }
        val episodes = document.select("div.lstepsiode ul li").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null
            val ep = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(a.text())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            newEpisode(fixUrl(a.attr("href"))) { }
        }.reversed()
        return newAnimeLoadResponse(title, url, type) {
            posterUrl = poster
            plot = description
            this.tags = tags
            this.year = year
            showStatus = status
            addEpisodes(DubStatus.Subbed, episodes)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("div#downloadb li").amap { li ->
            val quality = li.select("strong").text()
            li.select("a").amap { a ->
                loadFixedExtractor(fixUrl(a.attr("href")), quality, subtitleCallback, callback)
            }
        }
        return true
    }

    private suspend fun loadFixedExtractor(
        url: String,
        quality: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        loadExtractor(url, subtitleCallback = subtitleCallback) { link ->
            runBlocking {
                callback(
                    newExtractorLink(link.name, link.name, link.url, link.type) {
                        referer = link.referer
                        this.quality = quality.fixQuality()
                        headers = link.headers
                        extractorData = link.extractorData
                    }
                )
            }
        }
    }

    private fun String.fixQuality(): Int = when (uppercase()) {
        "4K" -> Qualities.P2160.value
        "FULLHD" -> Qualities.P1080.value
        "MP4HD" -> Qualities.P720.value
        else -> filter { it.isDigit() }.toIntOrNull() ?: Qualities.Unknown.value
    }

    private fun String.removeBloat(): String =
        replace(Regex("(Nonton|Anime|Subtitle\\sIndonesia)", RegexOption.IGNORE_CASE), "").trim()
}
