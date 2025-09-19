package com.dramaid

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

open class Dramaid : MainAPI() {
    override var mainUrl = "https://dramaid.nl"
    override var name = "DramaId"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.AsianDrama)

    // ========= Scraping dasar =========

    override val mainPage = mainPageOf(
        "&status=&type=&order=update" to "Drama Terbaru",
        "&order=latest" to "Baru Ditambahkan",
        "&status=&type=&order=popular" to "Drama Popular",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/series/?page=$page${request.data}").document
        val home = doc.select("article[itemscope=itemscope]").mapNotNull {
            val href = it.selectFirst("a.tip")?.attr("href") ?: return@mapNotNull null
            val title = it.selectFirst("h2[itemprop=headline]")?.text() ?: return@mapNotNull null
            newTvSeriesSearchResponse(title, href, TvType.AsianDrama)
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("article[itemscope=itemscope]").mapNotNull {
            val href = it.selectFirst("a.tip")?.attr("href") ?: return@mapNotNull null
            val title = it.selectFirst("h2[itemprop=headline]")?.text() ?: return@mapNotNull null
            newTvSeriesSearchResponse(title, href, TvType.AsianDrama)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text() ?: "No Title"
        val episodes = doc.select(".eplister li a").map {
            newEpisode(it.attr("href")) { this.name = it.text() }
        }.reversed()
        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes = episodes)
    }

    // ========= Model JSON dari API =========

    private data class ApiResponse(
        @JsonProperty("sources") val sources: List<Source>?,
        @JsonProperty("tracks") val tracks: List<Track>?
    )

    private data class Source(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?
    )

    private data class Track(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?
    )

    // ========= Ambil link dari API =========

    private suspend fun invokeDriveSource(
        url: String,
        subCallback: (SubtitleFile) -> Unit,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("/").substringBefore("?")

        val payload = mapOf(
            "query" to mapOf(
                "source" to "db",
                "id" to id,
                "download" to ""
            )
        )

        val json = app.post("https://miku.gdrive.web.id/api/", data = payload)
            .parsedSafe<ApiResponse>() ?: return

        json.sources?.forEach { src ->
            val videoUrl = src.file ?: return@forEach
            sourceCallback(
                ExtractorLink(
                    source = name,
                    name = src.label ?: "GDrive",
                    url = videoUrl,
                    referer = url,
                    quality = getQualityFromName(src.label ?: ""),
                    type = if (videoUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.MP4
                )
            )
        }

        json.tracks?.forEach { track ->
            val file = track.file ?: return@forEach
            subCallback(SubtitleFile(track.label ?: "Subtitle", file))
        }
    }

    // ========= loadLinks =========

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val sources = doc.select("#pembed iframe, .mirror > option").mapNotNull {
            val src = if (it.hasAttr("value")) Jsoup.parse(base64Decode(it.attr("value"))).select("iframe").attr("src")
            else it.attr("src")
            fixUrlNull(src)
        }

        sources.amap {
            if (it.contains("gdrive.web.id")) {
                invokeDriveSource(it, subtitleCallback, callback)
            } else {
                loadExtractor(it, "$mainUrl/", subtitleCallback, callback)
            }
        }

        return true
    }
}
