package com.ngefilm

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile

// -------------------- Bangjago --------------------
class Bangjago : ExtractorApi() {
    override val name = "Bangjago"
    override val mainUrl = "https://bangjago.upns.blog"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {
        val page = app.get(url, referer = referer ?: mainUrl).text

        // cari link master.m3u8
        val masterUrl = Regex("""https.*?master\.m3u8[^"']*""")
            .find(page)?.value ?: return null

        // tambahkan header agar sama dengan request di cURL
        val headers = mapOf(
            "Referer" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        return M3u8Helper.generateM3u8(
            name,
            masterUrl,
            mainUrl,
            headers = headers
        ) ?: emptyList()
    }
}


// -------------------- PlayerNgefilm21 --------------------
class PlayerNgefilm21 : ExtractorApi() {
    override val name = "PlayerNgefilm21"
    override val mainUrl = "https://playerngefilm21.rpmlive.online"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {
        val page = app.get(url, referer = referer ?: mainUrl).text

        // Cari master.m3u8
        val masterUrl = Regex("""https.*?master\.m3u8[^"']*""")
            .find(page)?.value ?: return null

        // Headers supaya sama dengan request browser
        val headers = mapOf(
            "Referer" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        return M3u8Helper.generateM3u8(
            name,
            masterUrl,
            mainUrl,
            headers = headers
        ) ?: emptyList()
    }
}


class Bingezone : ExtractorApi() {
    override val name = "Bingezone"
    override val mainUrl = "https://bingezove.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {
        // Ambil halaman embed
        val page = app.get(url, referer = referer ?: mainUrl).text

        // Cari link master.m3u8
        val masterUrl = Regex("""https.*?master\.m3u8[^"']*""")
            .find(page)?.value ?: return null

        // Header harus pakai referer embed, bukan mainUrl
        val headers = mapOf(
            "Referer" to url,
            "User-Agent" to USER_AGENT
        )

        return M3u8Helper.generateM3u8(
            name,
            masterUrl,
            mainUrl,
            headers = headers
        ) ?: emptyList()
    }
}
