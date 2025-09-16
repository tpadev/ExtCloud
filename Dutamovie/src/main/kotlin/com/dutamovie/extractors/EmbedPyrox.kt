package com.dutamovie.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.ExtractorApi
import com.lagradost.cloudstream3.utils.*

class EmbedPyrox : ExtractorApi() {
    override val name = "EmbedPyrox"
    override val mainUrl = "https://embedpyrox.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Ambil token dari url iframe
        val token = url.substringAfter("video/").substringBefore("?")
        val apiUrl = "$mainUrl/player/index.php?data=$token&do=getVideo"

        // Kirim POST ke API FireHLS
        val res = app.post(
            apiUrl,
            referer = mainUrl,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
            )
        ).text

        // Cari link m3u8 dari response
        val m3u8 = Regex("https?://[^\"]+\\.m3u8").find(res)?.value
        if (m3u8 != null) {
            M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
            return true
        }

        return false
    }
}
