package com.dramaid

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

open class Dramaid : MainAPI() {
    override var mainUrl = "https://dramaid.nl"
    override var name = "DramaId"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.AsianDrama)

    companion object {
        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }

        fun getType(t: String?): TvType {
            return when {
                t?.contains("Movie", true) == true -> TvType.Movie
                t?.contains("Anime", true) == true -> TvType.Anime
                else -> TvType.AsianDrama
            }
        }
    }

    override val mainPage = mainPageOf(
        "&status=&type=&order=update" to "Drama Terbaru",
        "&order=latest" to "Baru Ditambahkan",
        "&status=&type=&order=popular" to "Drama Popular",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/series/?page=$page${request.data}").document
        val home = document.select("article[itemscope=itemscope]").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperDramaLink(uri: String): String {
        return if (uri.contains("-episode-")) {
            "$mainUrl/series/" + Regex("$mainUrl/(.+)-ep.+").find(uri)?.groupValues?.get(1)
        } else {
            uri
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = getProperDramaLink(this.selectFirst("a.tip")!!.attr("href"))
        val title = this.selectFirst("h2[itemprop=headline]")?.text()?.trim() ?: return null
        val posterUrl = fixUrlNull(this.select("img:last-child").attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article[itemscope=itemscope]").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster = fixUrlNull(document.select("div.thumb img:last-child").attr("src"))
        val tags = document.select(".genxed > a").map { it.text() }
        val type = document.selectFirst(".info-content .spe span:contains(Tipe:)")?.ownText()
        val year = Regex("\\d, ([0-9]*)").find(
            document.selectFirst(".info-content > .spe > span > time")!!.text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val status = getStatus(
            document.select(".info-content > .spe > span:nth-child(1)")
                .text().trim().replace("Status: ", "")
        )
        val description = document.select(".entry-content > p").text().trim()

        val episodes = document.select(".eplister > ul > li").mapNotNull { episodeElement ->
            val anchor = episodeElement.selectFirst("a") ?: return@mapNotNull null
            val link = fixUrl(anchor.attr("href"))
            val episodeTitle = episodeElement.selectFirst("a > .epl-title")?.text() ?: anchor.text()

            val episodeNumber = Regex("""(?:Episode|Eps)\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(episodeTitle)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

            newEpisode(link) {
                this.name = episodeTitle
                this.episode = episodeNumber
            }
        }.reversed()

        val recommendations =
            document.select(".listupd > article[itemscope=itemscope]").mapNotNull { rec ->
                rec.toSearchResult()
            }

        return newTvSeriesLoadResponse(
            title,
            url,
            getType(type),
            episodes = episodes
        ) {
            posterUrl = poster
            this.year = year
            showStatus = status
            plot = description
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    // === DATA CLASS untuk API JSON ===
    private data class ApiResponse(
        @JsonProperty("sources") val sources: List<Source>?,
        @JsonProperty("tracks") val tracks: List<Track>?
    )

    private data class Source(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?,
        @JsonProperty("type") val type: String?
    )

    private data class Track(
        @JsonProperty("file") val file: String?,
        @JsonProperty("label") val label: String?
    )

    // === invokeDriveSource pakai endpoint /api/ ===
    private suspend fun invokeDriveSource(
        url: String,
        subCallback: (SubtitleFile) -> Unit,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("/")
        val apiUrl = "https://miku.gdrive.web.id/api/"

        // Cloudstream 4.x masih pakai data=Map<String,String>
        val json = app.post(apiUrl, data = mapOf("id" to id))
            .parsedSafe<ApiResponse>() ?: return

        json.sources?.forEach { src ->
            val videoUrl = src.file ?: return@forEach
            sourceCallback(
                newExtractorLink(
                    this.name,
                    src.label ?: "GDrive",
                    videoUrl
                ) {
                    referer = url
                    quality = getQualityFromName(src.label ?: "")
                    isM3u8 = videoUrl.endsWith(".m3u8")
                }
            )
        }

        json.tracks?.forEach { tr ->
            subCallback.invoke(
                SubtitleFile(tr.label ?: "Subtitle", tr.file ?: return@forEach)
            )
        }
    }

    // === loadLinks ===
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val sources = document.select("#pembed iframe, .mirror > option").mapNotNull {
            val src = if (it.hasAttr("value")) {
                Jsoup.parse(base64Decode(it.attr("value")))
                    .select("iframe").attr("src")
            } else {
                it.attr("src")
            }
            fixUrlNull(src)
        }

        sources.amap {
            when {
                it.contains("gdrive.web.id") -> {
                    invokeDriveSource(it, subtitleCallback, callback)
                }
                else -> loadExtractor(it, "$mainUrl/", subtitleCallback, callback)
            }
        }

        return true
    }
}
