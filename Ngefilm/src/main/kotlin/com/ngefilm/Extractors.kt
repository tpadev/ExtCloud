package com.ngefilm

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Gdriveplayer
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URI

class Movearnpre : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://movearnpre.com"
}

class Dhtpre : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://dhtpre.com"
}

class Mivalyo : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://mivalyo.com"
}

class Bingezove : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://bingezove.com"
}

open class Dingtezuni : ExtractorApi() {
    override val name = "Earnvids"
    override val mainUrl = "https://dingtezuni.com"
    override val requiresReferer = true

 override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
	        "User-Agent" to USER_AGENT,
        )
        
        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if(result.contains("var links")){
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        // m3u8 urls could be prefixed by 'file:', 'hls2:' or 'hls4:', so we just match ':'
        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            generateM3u8(
                name,
                fixUrl(m3u8Match.groupValues[1]),
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
        }
    }

    private fun getEmbedUrl(url: String): String {
		return when {
			url.contains("/d/") -> url.replace("/d/", "/v/")
			url.contains("/download/") -> url.replace("/download/", "/v/")
			url.contains("/file/") -> url.replace("/file/", "/v/")
			else -> url.replace("/f/", "/v/")
		}
	}

}

class Hglink : StreamWishExtractor() {
    override val name = "Hglink"
    override val mainUrl = "https://hglink.to"
}

class Gdriveplayerto : Gdriveplayer() {
    override val mainUrl: String = "https://gdriveplayer.to"
}

class Playerngefilm21 : VidStack() {
    override var name = "Playerngefilm21"
    override var mainUrl = "https://playerngefilm21.rpmlive.online"
    override var requiresReferer = true
}

class P2pplay : VidStack() {
    override var name = "P2pplay"
    override var mainUrl = "https://nf21.p2pplay.pro"
    override var requiresReferer = true
}

open class XShotCok : ExtractorApi() {
    override val name = "XShotCok"
    override val mainUrl = "https://xshotcok.com"
    override val requiresReferer = false
    open val redirect = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        val document = app.get(url, allowRedirects = redirect, referer = referer).document
        with(document) {
            this.select("script").map { script ->
                if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                    val data =
                        getAndUnpack(script.data()).substringAfter("sources:[").substringBefore("]")
                    tryParseJson<List<ResponseSource>>("[$data]")?.map {
                        sources.add(
                            newExtractorLink(
                                name,
                                name,
                                it.file,
                            ) {
                                this.referer = mainUrl
                                this.quality = when {
                                    url.contains("hxfile.co") -> getQualityFromName(
                                        Regex("\\d\\.(.*?).mp4").find(
                                            document.select("title").text()
                                        )?.groupValues?.get(1).toString()
                                    )
                                    else -> getQualityFromName(it.label)
                                }
                            }
                        )
                    }
                } else if (script.data().contains("\"sources\":[")) {
                    val data = script.data().substringAfter("\"sources\":[").substringBefore("]")
                    tryParseJson<List<ResponseSource>>("[$data]")?.map {
                        sources.add(
                            newExtractorLink(
                                name,
                                name,
                                it.file,
                            ) {
                                this.referer = mainUrl
                                this.quality = when {
                                    it.label?.contains("HD") == true -> Qualities.P720.value
                                    it.label?.contains("SD") == true -> Qualities.P480.value
                                    else -> getQualityFromName(it.label)
                                }
                            }
                        )
                    }
                }
                else {
                    null
                }
            }
        }
        return sources
    }

    private data class ResponseSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("label") val label: String?
    )

}

class Dsvplay : DoodLaExtractor() {
    override var mainUrl = "https://dsvplay.com"
}