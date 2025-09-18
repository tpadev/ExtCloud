package com.dutamovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import kotlin.math.roundToInt

class DutaMovie : MainAPI() {

    override var mainUrl = "https://justilien.com"
    private var directUrl: String? = null
    override var name = "DutaMovie"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
        mainPageOf(
            "category/box-office/page/%d/" to "Box Office",
            "category/serial-tv/page/%d/" to "Serial TV",
            "category/animation/page/%d/" to "Animasi",
            "country/korea/page/%d/" to "Serial TV Korea",
            "country/indonesia/page/%d/" to "Serial TV Indonesia",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr()).fixImageQuality()
        val quality =
            this.select("div.gmr-qual, div.gmr-quality-item > a").text().trim().replace("-", "")
        return if (quality.isEmpty()) {
            val episode =
                Regex("Episode\\s?([0-9]+)")
                    .find(title)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: this.select("div.gmr-numbeps > span").text().toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document =
            app.get("${mainUrl}?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L).document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("a > span.idmuvi-rp-title")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr().fixImageQuality())
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun load(url: String): LoadResponse {
        val fetch = app.get(url)
        directUrl = getBaseUrl(fetch.url)
        val document = fetch.document

        val title =
            document.selectFirst("h1.entry-title")
                ?.text()
                ?.substringBefore("Season")
                ?.substringBefore("Episode")
                ?.trim()
                .orEmpty()

        val poster =
            fixUrlNull(document.selectFirst("figure.pull-left > img")?.getImageAttr())
                ?.fixImageQuality()

        val tags =
            document.select("div.gmr-moviedata strong:contains(Genre:) > a").map { it.text() }

        val year =
            document.select("div.gmr-moviedata strong:contains(Year:) > a")
                .text()
                .trim()
                .toIntOrNull()

        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div[itemprop=description] > p")?.text()?.trim()
        val trailer = document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")?.attr("href")
        val rating =
            document.selectFirst("div.gmr-meta-rating > span[itemprop=ratingValue]")
                ?.text()?.toRatingInt()
        val actors =
            document.select("div.gmr-moviedata").last()?.select("span[itemprop=actors]")?.map {
                it.select("a").text()
            }

        val recommendations =
            document.select("div.idmuvi-rp ul li").mapNotNull { it.toRecommendResult() }

        return if (tvType == TvType.TvSeries) {
            val episodes =
                document.select("div.vid-episodes a, div.gmr-listseries a")
                    .map { eps ->
                        val href = fixUrl(eps.attr("href"))
                        val name = eps.text()
                        val episode =
                            name.split(" ")
                                .lastOrNull()
                                ?.filter { it.isDigit() }
                                ?.toIntOrNull()
                        val season =
                            name.split(" ")
                                .firstOrNull()
                                ?.filter { it.isDigit() }
                                ?.toIntOrNull()
                        newEpisode(href) {
                            this.name = name
                            this.episode = episode
                            this.season = if (name.contains(" ")) season else null
                        }
                    }
                    .filter { it.episode != null }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val referer = directUrl?.let { if (it.endsWith("/")) it else "$it/" } ?: mainUrl
        val baseResponse = app.get(data)
        val baseDocument = baseResponse.document
        val streamVisited = mutableSetOf<String>()
        val downloadVisited = mutableSetOf<String>()
        val switchRegex = Regex("switchVideo\\(['\"](.*?)['\"]\\)", RegexOption.IGNORE_CASE)
        val fallbackHostBlacklist = setOf("embedpyrox.xyz", "helvid.net", "pm21.p2pplay.pro")

        fun resolveUrl(rawUrl: String?): String? {
            if (rawUrl.isNullOrBlank()) return null
            val trimmed = rawUrl.trim()
            if (trimmed == "#" || trimmed.startsWith("javascript", ignoreCase = true)) return null
            return when {
                trimmed.startsWith("//") -> httpsify("https:$trimmed")
                trimmed.startsWith("http", ignoreCase = true) -> httpsify(trimmed)
                else -> runCatching {
                    val base = (directUrl ?: mainUrl).let { if (it.endsWith("/")) it else "$it/" }
                    httpsify(URI(base).resolve(trimmed).toString())
                }.getOrNull()
            }
        }

        fun hostOf(url: String?): String? =
            runCatching { url?.let { URI(it).host?.removePrefix("www.") } }.getOrNull()

        /** Try to turn …/index-*.txt or cf-master.txt into a real m3u8, or extract m3u8 inside */
        suspend fun trySmartSolutionsLink(url: String): Boolean {
            if (!url.contains("smartsolutionsforplayers.site")) return false
            val headers = mapOf(
                "Referer" to referer,
                "Origin" to getBaseUrl(url),
                "User-Agent" to USER_AGENT
            )

            // 1) Simple “.txt → .m3u8” flip (paling sering berhasil)
            val candidate = url.replace(".txt", ".m3u8").substringBefore("#")
            val try1 = runCatching { app.get(candidate, headers = headers).text }.getOrNull()
            if (try1?.startsWith("#EXTM3U") == true) {
                callback(
                    ExtractorLink(
                        name, "HLS", candidate, referer,
                        Qualities.Unknown.value, true, headers
                    )
                )
                streamVisited.add(candidate)
                return true
            }

            // 2) Kalau .txt berisi config/JSON, cari URL m3u8 di dalamnya
            val raw = runCatching { app.get(url, headers = headers).text }.getOrNull() ?: return false
            val m3u8Regex = Regex("""https?://[A-Za-z0-9\-\._~:/\?#\[\]@!\$&'\(\)\*\+,;%=]+\.m3u8""")
            val found = m3u8Regex.find(raw)?.value
            if (found != null) {
                val ok = runCatching { app.get(found, headers = headers).text }.getOrNull()
                if (ok?.startsWith("#EXTM3U") == true) {
                    callback(
                        ExtractorLink(
                            name, "HLS", found, referer,
                            Qualities.Unknown.value, true, headers
                        )
                    )
                    streamVisited.add(found)
                    return true
                }
            }
            return false
        }

        /** Parse halaman downloader p2pplay (dapatkan mp4 / link tontonan) */
        suspend fun handleP2pplayPage(pageUrl: String) {
            val doc = app.get(pageUrl, referer = referer).document

            // tombol "Watch Online" kadang kembali ke hash player, biarkan extractor umum yang handle
            doc.select("a.downloader-button[href]").forEach { a ->
                val link = resolveUrl(a.attr("href")) ?: return@forEach
                val text = a.text().ifBlank { "Download" }
                if (downloadVisited.add(link)) {
                    val host = hostOf(link)
                    // kirim sebagai tautan download biasa (mp4)
                    callback(
                        ExtractorLink(
                            name,
                            "$text (Download)",
                            link,
                            referer,
                            Qualities.Unknown.value,
                            link.contains(".m3u8", true),
                            headers = mapOf("Referer" to referer, "User-Agent" to USER_AGENT)
                        )
                    )
                    if (host != null && !fallbackHostBlacklist.contains(host)) {
                        streamVisited.add(link)
                    }
                }
            }
        }

        suspend fun handlePage(document: Document) {
            val embeds = linkedSetOf<String>()

            // iframe utama
            document.select(
                "div.gmr-embed-responsive iframe, div.gmr-pagi-player iframe," +
                        " iframe[data-litespeed-src], iframe#video-frame"
            ).forEach { iframe ->
                resolveUrl(iframe.getIframeAttr())?.let(embeds::add)
            }

            // data-video attributes
            document.select("[data-video]").forEach { element ->
                resolveUrl(element.attr("data-video"))?.let(embeds::add)
            }

            // onclick switchVideo('…')
            document.select("[onclick*=switchVideo]").forEach { element ->
                switchRegex.findAll(element.attr("onclick")).forEach { m ->
                    resolveUrl(m.groupValues.getOrNull(1))?.let(embeds::add)
                }
            }
            document.select("script").forEach { element ->
                switchRegex.findAll(element.data()).forEach { m ->
                    resolveUrl(m.groupValues.getOrNull(1))?.let(embeds::add)
                }
            }

            // proses kandidat
            for (u in embeds) {
                if (!streamVisited.add(u)) continue
                val host = hostOf(u).orEmpty()

                // 1) Halaman p2pplay (hash) → buka downloader, kirim mp4
                if (host.contains("p2pplay.pro")) {
                    handleP2pplayPage(u)
                }

                // 2) smartsolutionsforplayers: .txt/.m3u8
                if (host.contains("smartsolutionsforplayers.site")) {
                    if (trySmartSolutionsLink(u)) continue
                }

                // 3) fallback ke extractor umum
                loadExtractor(u, referer, subtitleCallback, callback)
            }

            // Link “Download” di halaman konten
            document.select("#gmr-id-download a[href], .gmr-download-list a[href]").forEach { a ->
                val link = resolveUrl(a.attr("href")) ?: return@forEach
                if (!downloadVisited.add(link)) return@forEach
                if (!streamVisited.contains(link)) {
                    // kalau link-nya juga playable, coba extractor
                    loadExtractor(link, referer, subtitleCallback, callback)
                }
                val host = hostOf(link)
                if (host == null || host !in fallbackHostBlacklist) {
                    val displayName = a.text().ifBlank { host ?: "Download" }
                    callback(
                        ExtractorLink(
                            name, "$displayName (Download)", link, referer,
                            Qualities.Unknown.value, link.contains(".m3u8", true),
                            headers = mapOf("Referer" to referer, "User-Agent" to USER_AGENT)
                        )
                    )
                }
            }
        }

        // Kumpulkan semua tab Server (Server 1/2/3…)
        val pageUrls = buildList {
            add(data)
            baseDocument.select("ul.muvipro-player-tabs li a[href]")
                .mapNotNull { resolveUrl(it.attr("href")) }
                .forEach { add(it) }
        }.distinct()

        pageUrls.amap { pageUrl ->
            val doc = if (pageUrl.equals(data, ignoreCase = true)) baseDocument else app.get(pageUrl).document
            handlePage(doc)
        }

        return streamVisited.isNotEmpty() || downloadVisited.isNotEmpty()
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

    private fun String?.toRatingInt(): Int? {
        if (this.isNullOrBlank()) return null
        val normalized = this.replace(',', '.').trim()
        val percentValue = normalized.removeSuffix("%").toIntOrNull()
        if (normalized.endsWith("%") && percentValue != null) return percentValue
        val value = normalized.toFloatOrNull() ?: return null
        return (value * 10).roundToInt()
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }

    private fun getBaseUrl(url: String): String {
        val u = URI(url)
        return "${u.scheme}://${u.host}"
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
