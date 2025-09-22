package com.ngefilm.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.httpsify

/**
 * Host custom yang bukan bawaan CS3:
 * - PlayerNgefilm21
 * - Bangjago
 * - Hglink
 * - BingeZove
 *
 * Pola:
 * - Kalau halaman embed hanya "proxy" -> ambil <iframe src> lalu lempar ke loadExtractor() lagi.
 * - Kalau halaman embed mengandung langsung .m3u8 -> gunakan M3u8Helper.
 */

// Proxy embed → ambil iframe → lempar ke extractor lain
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
        val html = app.get(httpsify(url), referer = referer ?: mainUrl).text
        val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
            ?.groupValues?.getOrNull(1)
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}

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
        val html = app.get(httpsify(url), referer = referer ?: mainUrl).text
        val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
            ?.groupValues?.getOrNull(1)
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}

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
        val html = app.get(httpsify(url), referer = referer ?: mainUrl).text
        val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
            ?.groupValues?.getOrNull(1)
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}

// Sering langsung expose .m3u8 di halaman
class BingeZove : ExtractorApi() {
    override val name = "BingeZove"
    override val mainUrl = "https://bingezove.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = app.get(httpsify(url)).text
        val m3u8 = Regex("""https?://[^\s"'<>]+\.m3u8""", RegexOption.IGNORE_CASE).find(html)?.value
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
        } else {
            // fallback: kalau ternyata masih iframe, lempar lagi
            val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
                ?.groupValues?.getOrNull(1)
            if (!iframe.isNullOrBlank()) {
                loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
            }
        }
    }
}
