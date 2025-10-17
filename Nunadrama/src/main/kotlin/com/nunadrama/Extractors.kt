package com.nunadrama

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class Terabox : ExtractorApi() {
    override val name = "Terabox"
    override val mainUrl = "https://www.terabox.com"
    private val baseUrl = "https://www.1024terabox.com"
    private val apiUrl = "https://terabox-pro-api.vercel.app/api?link="
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(apiUrl + url)
        val apiResponse = tryParseJson<AlphaApiResponse>(response.text) ?: return

        if (apiResponse.status.equals("success", true)) {
            apiResponse.extractedInfo?.forEach { data ->
                val linkUrl = data.downloadUrl ?: return@forEach
                callback.invoke(
                    newExtractorLink(
                        name,
                        data.title ?: name,
                        linkUrl,
                        INFER_TYPE
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = getQualityFromName(data.title ?: "")
                    }
                )
            }
        }
    }

    data class AlphaApiResponse(
        @JsonProperty("status") val status: String,
        @JsonProperty("📋 Extracted Info") val extractedInfo: List<AlphaData>?
    )

    data class AlphaData(
        @JsonProperty("📄 Title") val title: String?,
        @JsonProperty("🔗 Direct Download Link") val downloadUrl: String?
    )
}