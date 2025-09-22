package com.ngefilm.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

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

        // cari langsung m3u8 di halaman
        val m3u8 = Regex("""https?://[^\s'"]+\.m3u8""").find(html)?.value
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
            return
        }

        // fallback: cari iframe
        val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)

        if (!iframe.isNullOrBlank()) {
            val iframeHtml = app.get(httpsify(iframe), referer = url).text
            val iframeM3u8 = Regex("""https?://[^\s'"]+\.m3u8""").find(iframeHtml)?.value
            if (!iframeM3u8.isNullOrBlank()) {
                M3u8Helper.generateM3u8(name, iframeM3u8, mainUrl).forEach(callback)
            } else {
                loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
            }
        }
    }
}

class Hglink : ExtractorApi() {
    override val name = "HgLink"
    override val mainUrl = "https://hglink.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = app.get(httpsify(url), referer = referer ?: mainUrl).text
        val m3u8 = Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""").find(html)?.value
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, url).forEach(callback)
            return
        }

        val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']").find(html)?.groupValues?.getOrNull(1)
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}


// Sering langsung expose .m3u8 di halaman
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
        val res = app.get(httpsify(url), referer = referer ?: mainUrl)
        val html = res.text

        // cari langsung link m3u8
        val m3u8 = Regex("""https?://[^\s"']+\.m3u8""").find(html)?.value
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, url).forEach(callback)
            return
        }

        // fallback: cek iframe
        val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)

        if (!iframe.isNullOrBlank()) {
            val iframeRes = app.get(httpsify(iframe), referer = url)
            val iframeHtml = iframeRes.text
            val iframeM3u8 = Regex("""https?://[^\s"']+\.m3u8""").find(iframeHtml)?.value
            if (!iframeM3u8.isNullOrBlank()) {
                M3u8Helper.generateM3u8(name, iframeM3u8, iframe).forEach(callback)
            } else {
                loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
            }
        }
    }
}

