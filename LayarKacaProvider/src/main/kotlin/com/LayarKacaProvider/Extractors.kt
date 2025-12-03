package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject


class Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

class F16px : Filesim() {
    override val mainUrl = "https://f16px.com"
    override val name = "F16px"
}

class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://cloud.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        // Langkah 1: ambil HTML video.php?id=xxx
        val html = app.get(url, referer = url).text

        // Langkah 2: sesuai website — file video ada di JS sebagai "file: "....m3u8"
        val file = Regex("""file\s*:\s*"([^"]+\.m3u8[^"]*)"""")
            .find(html)
            ?.groupValues?.get(1)

        if (file != null) {
            // Langkah 3: generate link m3u8
            M3u8Helper.generateM3u8(
                name,
                file,
                url
            ).forEach(callback)
            return
        }

        Log.e("Hownetwork", "Tidak menemukan link M3U8 pada halaman.")
    }
}

class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "723qrh1p.fun"
}

class Turbovidhls : Filesim() {
    override val name = "Turbovidhls"
    override var mainUrl = "https://emturbovid.com"
}
