package com.anoboy

import com.lagradost.cloudstream3.*  
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors  
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore  
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer  
import com.lagradost.cloudstream3.MainAPI  
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.base64Decode 
import com.lagradost.cloudstream3.TvType  
import com.lagradost.cloudstream3.mainPageOf     
import com.lagradost.cloudstream3.newEpisode  
import com.lagradost.cloudstream3.utils.*  
import org.jsoup.nodes.Element
import org.jsoup.Jsoup

class Anoboy : MainAPI() {
    override var mainUrl = "https://anoboy.be"
    override var name = "Anoboy🦀"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getType(t: String?): TvType {
            if (t == null) return TvType.Anime
            return when {
                t.contains("Tv", true) -> TvType.Anime
                t.contains("Movie", true) -> TvType.AnimeMovie
                t.contains("OVA", true) || t.contains("Special", true) -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus {
            if (t == null) return ShowStatus.Completed
            return when {
                t.contains("Sedang Tayang", true) -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update" to "Update Terbaru",
        "anime/?sub=&order=latest" to "Baru ditambahkan",
        "anime/?status=&type=&order=popular" to "Terpopuler",
        "anime/?sub=&order=rating" to "Rating Tertinggi",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/${request.data}".plus("&page=$page")
        val document = app.get(url).document
        val items = document.select("div.listupd article.bs")
                            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, items), hasNext = items.isNotEmpty())
    }


    private fun Element.toSearchResult(): SearchResponse? {
    val linkElement = this.selectFirst("a") ?: return null
    val href = fixUrl(linkElement.attr("href"))
    val title = linkElement.attr("title").ifBlank {
        this.selectFirst("div.tt")?.text()
    } ?: return null
    val poster = this.selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }
    return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }
}

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?s=$query").document.select("div.listupd div.bs").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("div.infox h1")?.text().toString().replace("Sub Indo", "").trim()
        val poster = document.selectFirst("div.bigcontent img")?.getImageAttr()

        val table = document.selectFirst("div.infox div.spe")
        val type = getType(table?.selectFirst("span:contains(Jenis:)")?.ownText())
        val year =
            table?.selectFirst("span:contains(Rilis:)")?.ownText()?.substringAfterLast(",")?.trim()
                ?.toIntOrNull()
        val status = table?.selectFirst("span:contains(Status:) font")?.text()
        val trailer = document.selectFirst("div.trailer iframe")?.attr("src")
        val episodes = document.select("ul#daftarepisode > li").map {
            val link = fixUrl(it.selectFirst("a")!!.attr("href"))
            val name = it.selectFirst("a")?.text() ?: ""
            val episode =
                Regex("Episode\\s?(\\d+)").find(name)?.groupValues?.getOrNull(0)?.toIntOrNull()
            newEpisode(link) { this.episode = episode }
        }.reversed()

        val tracker = APIHolder.getTracker(listOf(title), TrackerType.getTypes(type), year, true)

        return newAnimeLoadResponse(title, url, type) {
            posterUrl = tracker?.image ?: poster
            backgroundPosterUrl = tracker?.cover
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = getStatus(status)
            plot = document.select("div.sinopsis p").text()
            this.tags = table?.select("span:contains(Genre:) a")?.map { it.text() }
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

    val document = app.get(data).document


    document.selectFirst("div.player-embed iframe")
        ?.getIframeAttr()
        ?.let { iframe ->
            loadExtractor(httpsify(iframe), data, subtitleCallback, callback)
        }


    val mirrorOptions = document.select("select.mirror option[value]:not([disabled])")

    for (opt in mirrorOptions) {
        val base64 = opt.attr("value")
        if (base64.isBlank()) continue

        try {
            // Fix untuk base64 yang diselipkan whitespace
            val cleanedBase64 = base64.replace("\\s".toRegex(), "")
            val decodedHtml = base64Decode(cleanedBase64)

            val iframeTag = Jsoup.parse(decodedHtml).selectFirst("iframe")

            val mirrorUrl = when {
                iframeTag?.attr("src")?.isNotBlank() == true ->
                    iframeTag.attr("src")
                iframeTag?.attr("data-src")?.isNotBlank() == true ->
                    iframeTag.attr("data-src")
                else -> null
            }

            if (!mirrorUrl.isNullOrBlank()) {
                loadExtractor(httpsify(mirrorUrl), data, subtitleCallback, callback)
            }

        } catch (e: Exception) {
            println("Mirror decode error: ${e.localizedMessage}")
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
}
