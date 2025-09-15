package com.layarkaca

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class LayarKaca : MainAPI() {
    override var mainUrl = "https://tv.lk21official.love"
    private var seriesUrl = "https://tv1.nontondrama.my"
    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ---------------- MAIN PAGE ----------------
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Film Terbaru",
        "$mainUrl/category/action/page/" to "Action Terbaru",
        "$mainUrl/category/horror/page/" to "Horror Terbaru",
        "$mainUrl/category/romance/page/" to "Romance Terbaru",
        "$mainUrl/category/comedy/page/" to "Comedy Terbaru",
        "$seriesUrl/series/page/" to "Series Update",
        "$seriesUrl/populer/page/" to "Series Unggulan",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // WordPress sites often use base for page 1 and "/page/N" for subsequent pages.
        val base = request.data
        val url = if (base.endsWith("/page/")) {
            if (page <= 1) base.removeSuffix("/page/") else base + page
        } else base + page
        val document = app.get(url).document

        // Support both LK21 (article-based) and "ml-item" card grids
        val home = document.select("div.ml-item, article, div.movie-item, div.item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    // ---------------- SEARCH ----------------
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article, div.ml-item, div.movie-item, div.item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Anchor and poster
        val a = this.selectFirst("a")
        val mlHref = a?.attr("href")?.trim()?.ifBlank { null } ?: return null
        val href = fixUrl(mlHref)

        // Title: try multiple sources in order
        val title = listOf(
            a?.attr("oldtitle"),
            a?.attr("title"),
            this.selectFirst(".mli-info h2, h2 a, h1 a, .title, .name")?.text(),
            this.selectFirst("img")?.attr("alt")
        ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: return null

        // Poster: common lazy attributes
        val posterRaw = listOf(
            this.select("img").attr("data-original"),
            this.select("img").attr("data-src"),
            this.selectFirst("img")?.attr("src"),
            this.selectFirst("img")?.attr("srcset")?.split(" ")?.firstOrNull()
        ).firstOrNull { !it.isNullOrBlank() } ?: ""
        val poster = fixUrlNull(posterRaw)

        val type = if (href.contains(seriesUrl)) TvType.TvSeries else TvType.Movie

        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        }
    }

    // ---------------- LOAD DETAIL ----------------
    override suspend fun load(url: String): LoadResponse? {
        // Follow intermediate redirect pages ("dialihkan ke nontondrama")
        var document = app.get(url).document
        runCatching {
            val refresh = document.selectFirst("meta[http-equiv=refresh i]")?.attr("content")
            val refreshUrl = refresh?.substringAfter("url=", "")?.trim()
            val ndAnchor = document.select("a[href*='nontondrama']").firstOrNull()?.attr("href")
            val target = listOf(refreshUrl, ndAnchor).firstOrNull { !it.isNullOrBlank() }
            if (!target.isNullOrBlank()) {
                document = app.get(fixUrl(target)).document
            }
        }

        val titleRaw = listOf(
            document.selectFirst("meta[property=og:title]")?.attr("content"),
            document.selectFirst("h1")?.text()
        ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: return null
        val title = titleRaw.substringBefore("(").trim()
        val year = Regex("\\((\\d{4})\\)").find(titleRaw)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val poster = fixUrlNull(
            listOf(
                document.selectFirst("meta[property=og:image]")?.attr("content"),
                document.selectFirst("meta[name=twitter:image]")?.attr("content"),
                document.selectFirst(".thumb img")?.attr("src"),
                document.selectFirst("img.img-thumbnail")?.attr("src")
            ).firstOrNull { !it.isNullOrBlank() }
        )

        val description = listOf(
            document.selectFirst("meta[property=og:description]")?.attr("content"),
            document.selectFirst("meta[name=description]")?.attr("content"),
            document.selectFirst("div.desc")?.text(),
            document.selectFirst(".entry-content")?.text(),
            document.selectFirst(".mvici-right p")?.text()
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
        val rating = document.selectFirst("span[itemprop=ratingValue]")?.text()?.toRatingInt()

        val tags = document.select("p:contains(Genre) a, .mvici-left p:contains(Genre) a").map { it.text() }
        val actors = document.select("p:contains(Bintang Film) a, .mvici-left p:contains(Bintang Film) a").map { it.text() }

        // cek episode (series)
        val episodeCandidates = document.select(
            "div#seasons a, .les-content a, a[href*='/episode/'], a[href*='-episode-']"
        )
            .filter { a ->
                val t = a.text().trim()
                t.contains("Episode", true) || t.matches(Regex("^(Ep\\.?\\s*)?\\d+.*$"))
            }
        val episodes = episodeCandidates
            .map { it.attr("href") to it.text().trim() }
            .distinctBy { it.first }
            .mapIndexed { index, (hrefE, textE) ->
                val epUrl = fixUrl(hrefE)
                val epNumber = Regex("(\\d+)").find(textE)?.groupValues?.getOrNull(1)?.toIntOrNull()
                newEpisode(epUrl) {
                    name = textE.ifBlank { "Episode ${epNumber ?: (index + 1)}" }
                    season = 1
                    episode = epNumber ?: (index + 1)
                    posterUrl = poster
                    runTime = null
                }
            }

        return if (episodes.isNotEmpty()) {
            // Series
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
            }
        } else {
            // Movie
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
            }
        }
    }

    // ---------------- LOAD LINKS ----------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val candidates = linkedSetOf<String>()

        // 1) Standard iframe(s)
        document.select("iframe#main-player, iframe[src]").forEach { el ->
            val s = el.attr("src").trim()
            if (s.isNotBlank()) candidates += fixUrl(s)
        }

        // 2) Meta refresh redirection
        document.select("meta[http-equiv=refresh i]").forEach { meta ->
            val refresh = meta.attr("content")
            val refreshUrl = refresh.substringAfter("url=", "").trim()
            if (refreshUrl.startsWith("http")) candidates += refreshUrl
        }

        // 3) Direct anchors to external player domains
        val hostHints = listOf("nontondrama", "lk21", "castplayer", "hydrax", "turbovip", "p2pstream", "embed", "player")
        document.select("a[href]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.startsWith("http") && hostHints.any { href.contains(it, true) }) candidates += href
        }

        // 4) Data attributes that sometimes carry the player url
        document.select("*[data-src], *[data-video], *[data-url], *[data-href]").forEach { el ->
            listOf("data-src", "data-video", "data-url", "data-href").forEach { k ->
                val v = el.attr(k).trim()
                if (v.startsWith("http") && hostHints.any { v.contains(it, true) }) candidates += v
            }
        }

        // 5) URLs inside scripts limited to known hosts
        val scriptBlock = document.select("script").joinToString("\n") { it.data() }
        Regex("https?://[\\w./%#?=&-]+", RegexOption.IGNORE_CASE).findAll(scriptBlock).forEach { m ->
            val u = m.value
            if (hostHints.any { u.contains(it, true) }) candidates += u
        }

        var found = false
        candidates.forEach { link ->
            try {
                loadExtractor(link, data, subtitleCallback, callback)
                found = true
            } catch (_: Throwable) {}
        }
        return found
    }
}
