package com.layarKacaProvider

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

// =====================================================
// Base extractor Hownetwork
// =====================================================
open class Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://stream.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // ambil id dari ?id=...
        val id = url.substringAfter("id=")
        val domain = URI(url).host ?: "stream.hownetwork.xyz"

        val res = app.post(
            "https://$domain/api.php?id=$id",
            data = mapOf(
                // fix pakai .sbs (bukan shop)
                "r" to "https://playeriframe.sbs/",
                "d" to domain,
            ),
            referer = url,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).parsedSafe<Sources>()

        res?.data?.forEach {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = it.file,
                    INFER_TYPE
                ) {
                    this.referer = url
                    this.quality = getQualityFromName(it.label)
                }
            )
        }
    }

    data class Sources(
        val data: ArrayList<Data>
    ) {
        data class Data(
            val file: String,
            val label: String?,
        )
    }
}

// =====================================================
// Variasi turunan Hownetwork (cloud domain)
// =====================================================
class Cloudhownetwork : Hownetwork() {
    override var mainUrl = "https://cloud.hownetwork.xyz"
}

// =====================================================
// Furher extractors (turunan dari Filesim)
// =====================================================
class Furher : Filesim() {
    override val name = "Furher"
    override var mainUrl = "https://furher.in"
}

class Furher2 : Filesim() {
    override val name = "Furher 2"
    override var mainUrl = "https://723qrh1p.fun"
}

// =====================================================
// Turbovidhls extractor
// =====================================================
class Turbovidhls : Filesim() {
    override val name = "Turbovidhls"
    override var mainUrl = "https://turbovidhls.com"
}
