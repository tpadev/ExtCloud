package com.layarkaca

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLinkType

/** =============== Extractors Lama =============== **/
open class Emturbovid : ExtractorApi() {
    override val name = "Emturbovid"
    override val mainUrl = "https://emturbovid.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val m3u8 = Regex("[\"'](.*?master\\.m3u8.*?)[\"']")
            .find(response.text)
            ?.groupValues?.getOrNull(1)

        M3u8Helper.generateM3u8(name, m3u8 ?: return, mainUrl).forEach(callback)
    }
}

open class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://cloud.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("id=")
        val res = app.post(
            "$mainUrl/api.php?id=$id",
            data = mapOf(
                "r" to "https://playeriframe.sbs/",
                "d" to "cloud.hownetwork.xyz"
            ),
            referer = "https://playeriframe.sbs/",
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Origin" to "https://playeriframe.sbs"
            )
        ).parsedSafe<Sources>()

        res?.data?.map {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    it.file,
                    referer ?: url,
                    getQualityFromName(it.label),
                    type = if (it.file.endsWith(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                )
            )
        }
    }

    data class Sources(val data: ArrayList<Data>) {
        data class Data(val file: String, val label: String?)
    }
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

/** =============== Extractors Baru =============== **/
class TurboVip : ExtractorApi() {
    override val name = "TurboVIP"
    override val mainUrl = "https://turbovip.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer).text
        val m3u8 = Regex("file:\"(.*?)\"").find(response)?.groupValues?.get(1) ?: return

        M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
    }
}

class Hydrax : ExtractorApi() {
    override val name = "Hydrax"
    override val mainUrl = "https://hydrax.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = referer).document
        val script = doc.selectFirst("script:containsData(sources)")?.data() ?: return
        val m3u8 = Regex("file:\"(.*?)\"").find(script)?.groupValues?.get(1) ?: return

        M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
    }
}

class CastPlayer : ExtractorApi() {
    override val name = "Cast"
    override val mainUrl = "https://castplayer.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url).text
        val m3u8 = Regex("file:\"(.*?)\"").find(res)?.groupValues?.get(1) ?: return

        M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
    }
}

class P2P : ExtractorApi() {
    override val name = "P2P"
    override val mainUrl = "https://p2pstream.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document
        val m3u8 = doc.select("source").attr("src")
        if (m3u8.isNotBlank()) {
            M3u8Helper.generateM3u8(name, m3u8, mainUrl).forEach(callback)
        }
    }
}
