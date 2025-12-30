package com.anoboy

import com.lagradost.cloudstream3.*   
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.SearchResponse  
import com.lagradost.cloudstream3.MainAPI  
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

    override suspend fun getMainPage(
    page: Int,
    request: MainPageRequest
): HomePageResponse {
    val document = app.get("$mainUrl/${request.data}&page=$page").document

    val items = document
        .select("div.listupd article.bs")
        .mapNotNull { it.toSearchResult() }

    return newHomePageResponse(
        HomePageList(request.name, items),
        hasNext = items.isNotEmpty()
    )
}


private fun Element.toSearchResult(): AnimeSearchResponse? {
    val a = selectFirst("a") ?: return null
    val title = a.attr("title").ifBlank {
        selectFirst("div.tt")?.text()
    } ?: return null

    val href = fixUrl(a.attr("href"))
    val poster = selectFirst("img")?.getImageAttr()?.let { fixUrlNull(it) }

    return newAnimeSearchResponse(title, href, TvType.Anime) {
        posterUrl = poster 
    }
}



   override suspend fun search(query: String): List<SearchResponse> {
    return app.get("$mainUrl/?s=$query")
        .document
        .select("div.listupd article.bs")
        .mapNotNull { it.toSearchResult() }
}


    override suspend fun load(url: String): LoadResponse {
    val document = app.get(url).document

    val title = document.selectFirst("h1.entry-title")
        ?.text()
        ?.replace("Subtitle Indonesia", "")
        ?.trim()
        ?: return null

    val poster = document.selectFirst("div.bigthumb img")
        ?.getImageAttr()
        ?.let { fixUrlNull(it) }

    val info = document.select("div.infox span")

    val type = getType(info.firstOrNull { it.text().contains("Jenis") }?.text())
    val status = info.firstOrNull { it.text().contains("Status") }?.text()
    val year = info.firstOrNull { it.text().contains("Rilis") }
        ?.text()
        ?.filter { it.isDigit() }
        ?.toIntOrNull()

    val episodes = document.select("div.eplister ul li").mapNotNull {
        val a = it.selectFirst("a") ?: return@mapNotNull null
        val link = fixUrl(a.attr("href"))
        val ep = it.selectFirst(".epl-num")?.text()?.toIntOrNull()

        newEpisode(link) {
            episode = ep
            name = it.selectFirst(".epl-title")?.text()
        }
    }.reversed()

    return newAnimeLoadResponse(title, url, type) {
        posterUrl = poster
        this.year = year
        addEpisodes(DubStatus.Subbed, episodes)
        showStatus = getStatus(status)
        plot = document.select("div.synp p").text()
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
