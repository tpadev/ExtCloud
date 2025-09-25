package com.ngefilm

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.SubtitleFile

// -------------------- Bangjago --------------------
class Bangjago : ExtractorApi() {
    override val name = "Bangjago"
    override val mainUrl = "https://bangjago.upns.blog"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {
        val page = app.get(url, referer = referer).text
        val masterUrl = Regex("""https.*?\.m3u8.*""")
            .find(page)?.value ?: return null

        return generateM3u8(
            name,
            masterUrl,
            mainUrl
        )
    }
}


// -------------------- PlayerNgefilm21 --------------------
class PlayerNgefilm21 : ExtractorApi() {
    override val name = "PlayerNgefilm21"
    override val mainUrl = "https://playerngefilm21.rpmlive.online"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {
        val page = app.get(url, referer = referer).text
        val masterUrl = Regex("""https.*?\.m3u8.*""")
            .find(page)?.value ?: return null

        return generateM3u8(
            name,
            masterUrl,
            mainUrl
        )
    }
}

class Bingezone : ExtractorApi() {
    override val name = "Bingezone"
    override val mainUrl = "https://bingezove.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {
        // Ambil halaman embed
        val page = app.get(url, referer = referer).text

        // Cari link master.m3u8
        val masterUrl = Regex("""https.*?\.m3u8.*""")
            .find(page)?.value ?: return null

        // Generate daftar kualitas dari master.m3u8
        return generateM3u8(
            name,
            masterUrl,
            mainUrl
        )
    }
}
