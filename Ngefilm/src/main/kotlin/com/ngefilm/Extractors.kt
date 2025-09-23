package com.ngefilm

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

// =================== PlayerNgefilm21 ===================
class PlayerNgefilm21 : ExtractorApi() {
    override val name = "PlayerNgefilm21"
    override val mainUrl = "https://playerngefilm21.rpmlive.online"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(httpsify(url), referer = referer ?: mainUrl).document

        val m3u8 = doc.selectFirst("source[src*=.m3u8]")?.attr("src")
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, url).forEach(callback)
            return
        }

        val iframe = doc.selectFirst("iframe")?.attr("src")
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}

// =================== Bangjago ===================
class Bangjago : ExtractorApi() {
    override val name = "Bangjago"
    override val mainUrl = "https://bangjago.upns.blog"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(httpsify(url), referer = referer ?: mainUrl).document

        val m3u8 = doc.selectFirst("source[src*=.m3u8]")?.attr("src")
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, url).forEach(callback)
            return
        }

        val iframe = doc.selectFirst("iframe")?.attr("src")
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}

// =================== Hglink ===================
class Hglink : ExtractorApi() {
    override val name = "Hglink"
    override val mainUrl = "https://hglink.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(httpsify(url), referer = referer ?: mainUrl).document

        val m3u8 = doc.selectFirst("source[src*=.m3u8]")?.attr("src")
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, url).forEach(callback)
            return
        }

        val iframe = doc.selectFirst("iframe")?.attr("src")
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}

// =================== BingeZove ===================
class BingeZove : ExtractorApi() {
    override val name = "BingeZove"
    override val mainUrl = "https://bingezove.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(httpsify(url), referer = referer ?: mainUrl).document

        val m3u8 = doc.selectFirst("source[src*=.m3u8]")?.attr("src")
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, url).forEach(callback)
            return
        }

        val iframe = doc.selectFirst("iframe")?.attr("src")
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}
