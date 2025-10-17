package com.nunadrama

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.fasterxml.jackson.annotation.JsonProperty

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
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(apiUrl + url)
            .get()
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        val body = response.body?.string() ?: return

        val apiResponse = mapper.readValue(body, AlphaApiResponse::class.java)

        if (apiResponse.status.equals("success", ignoreCase = true)) {
            apiResponse.extractedInfo?.forEach { data ->
                val link = ExtractorLink(
                    this.name,
                    data.title ?: this.name,
                    data.downloadUrl ?: return@forEach,
                    mainUrl,
                    getQualityFromName(data.title ?: "HD"),
                    isM3u8 = data.downloadUrl.endsWith(".m3u8")
                )
                callback.invoke(link)
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