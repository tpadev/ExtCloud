package com.ngefilm.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

/**
 * Custom Extractors untuk host Ngefilm:
 * - PlayerNgefilm21
 * - Bangjago
 * - Hglink
 * - BingeZove
 *
 * Pola:
 * - Jika halaman embed mengandung langsung .m3u8 → pakai M3u8Helper
 * - Jika halaman embed hanya proxy (iframe) → ambil src → parse ulang
 * - Jika semua gagal → lempar ke loadExtractor()
 */

// ============== PLAYER NGEFILM21 ==============
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

        // cari m3u8 langsung
        val m3u8 = Regex("""https?://[^\s'"]+\.m3u8""").find(html)?.value
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, url).forEach(callback)
            return
        }

        // fallback: iframe
        val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}

// ============== BANGJAGO ==============
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

        val m3u8 = Regex("""https?://[^\s'"]+\.m3u8""").find(html)?.value
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, url).forEach(callback)
            return
        }

        val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}

// ============== HGLINK ==============
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

        val m3u8 = Regex("""https?://[^\s'"]+\.m3u8""").find(html)?.value
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, url).forEach(callback)
            return
        }

        val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}

// ============== BINGEZOVE ==============
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

        val m3u8 = Regex("""https?://[^\s'"]+\.m3u8""").find(html)?.value
        if (!m3u8.isNullOrBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, url).forEach(callback)
            return
        }

        val iframe = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        if (!iframe.isNullOrBlank()) {
            loadExtractor(httpsify(iframe), url, subtitleCallback, callback)
        }
    }
}
