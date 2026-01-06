package com.samehadaku

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.*
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
        "daftar-anime-2/?type=TV&order=popular&page=" to "TV Populer",
        "daftar-anime-2/?type=OVA&order=title&page=" to "OVA",
        "daftar-anime-2/?type=Movie&order=title&page=" to "Movie"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        if (request.name == "Episode Terbaru") {
            val document = app.get(request.data + page).document
            val home = document.select("div.post-show ul li").mapNotNull {
                it.toEpisodeSearch()
            }
            return newHomePageResponse(
                HomePageList(request.name, home, true),
                hasNext = home.isNotEmpty()
            )
        }

        val document = app.get("$mainUrl/${request.data}$page").document
        val home = document.select("div.animposx").mapNotNull {
            it.toNormalSearch()
        }

        return newHomePageResponse(
            HomePageList(request.name, home, false),
            hasNext = home.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> =
        app.get("$mainUrl/?s=$query").document
            .select("div.animposx")
            .mapNotNull { it.toNormalSearch() }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")
            ?.text()
            ?.removeBloat()
            ?: return null

        val poster = document.selectFirst("div.thumb img")?.attr("src")
        val description = document.select("div.desc p").text()
        val tags = document.select("div.genre-info a").map { it.text() }

        val year = document.selectFirst("div.spe span:contains(Rilis)")
            ?.ownText()
            ?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }

        val status = when (
            document.selectFirst("div.spe span:contains(Status)")?.ownText()
        ) {
            "Ongoing" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }

        val type = when {
            url.contains("/movie/", true) -> TvType.AnimeMovie
            url.contains("/ova/", true) -> TvType.OVA
            else -> TvType.Anime
        }

        val trailer = document
            .selectFirst("iframe[src*=\"youtube\"]")
            ?.attr("src")

        val episodes = document.select("div.lstepsiode ul li")
            .mapNotNull {
                val a = it.selectFirst("a") ?: return@mapNotNull null
                val ep = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(a.text())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()

                newEpisode(fixUrl(a.attr("href"))) {
                    episode = ep
                }
            }
            .reversed()

        val tracker = APIHolder.getTracker(
            listOf(title),
            TrackerType.getTypes(type),
            year,
            true
        )

        return newAnimeLoadResponse(title, url, type) {
            posterUrl = tracker?.image ?: poster
            backgroundPosterUrl = tracker?.cover
            plot = description
            this.tags = tags
            this.year = year
            showStatus = status
            addEpisodes(DubStatus.Subbed, episodes)
            addTrailer(trailer)
            addMalId(tracker?.malId)
            addAniListId(tracker?.aniId?.toIntOrNull())
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        app.get(data).document
            .select("div#downloadb li")
            .amap { li ->
                val quality = li.select("strong").text()
                li.select("a").amap { a ->
                    loadExtractor(
                        fixUrl(a.attr("href")),
                        subtitleCallback = subtitleCallback
                    ) { link ->
                        callback(
                            newExtractorLink(
                                link.name,
                                link.name,
                                link.url,
                                link.type
                            ) {
                                referer = link.referer
                                headers = link.headers
                                extractorData = link.extractorData
                                this.quality = quality.fixQuality()
                            }
                        )
                    }
                }
            }
        return true
    }

    private fun Element.toEpisodeSearch(): AnimeSearchResponse? {
        val a = selectFirst("a") ?: return null
        val rawTitle = a.attr("title").ifBlank { a.text() }

        val ep = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(rawTitle)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val title = rawTitle
            .replace(Regex("Episode\\s*\\d+", RegexOption.IGNORE_CASE), "")
            .removeBloat()
            .trim()

        return newAnimeSearchResponse(title, fixUrl(a.attr("href")), TvType.Anime) {
            posterUrl = fixUrlNull(selectFirst("img")?.attr("src"))
            addDubStatus(DubStatus.Subbed)
            if (ep != null) addEpisodeNumber(ep)
        }
    }

    private fun Element.toNormalSearch(): AnimeSearchResponse? {
        val a = selectFirst("a") ?: return null
        val title = a.attr("title").ifBlank {
            selectFirst("div.title, h2.entry-title a")?.text()
        } ?: return null

        val href = fixUrl(a.attr("href"))
        val poster = fixUrlNull(selectFirst("img")?.attr("src"))

        val type = when {
            href.contains("/movie/", true) -> TvType.AnimeMovie
            href.contains("/ova/", true) -> TvType.OVA
            else -> TvType.Anime
        }

        return newAnimeSearchResponse(title.trim(), href, type) {
            posterUrl = poster
        }
    }

    private fun String.fixQuality(): Int = when (uppercase()) {
        "4K" -> Qualities.P2160.value
        "FULLHD" -> Qualities.P1080.value
        "HD" -> Qualities.P720.value
        else -> filter { it.isDigit() }.toIntOrNull() ?: Qualities.Unknown.value
    }

    private fun String.removeBloat(): String =
        replace(
            Regex("(Nonton|Anime|Subtitle\\s*Indonesia)", RegexOption.IGNORE_CASE),
            ""
        ).trim()
}
