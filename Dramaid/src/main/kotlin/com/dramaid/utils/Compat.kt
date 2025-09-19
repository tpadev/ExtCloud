package com.dramaid.utils

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class ExtractorLinkBuilder(
    val name: String,
    val source: String,
    val url: String
) {
    var referer: String = ""
    var quality: Int = Qualities.Unknown.value
    var isM3u8: Boolean = false
    var headers: Map<String, String> = emptyMap()
    var extractorData: String? = null
}

@Suppress("DEPRECATION", "DEPRECATION_ERROR")
fun newExtractorLink(
    name: String,
    source: String,
    url: String,
    builder: ExtractorLinkBuilder.() -> Unit = {}
): ExtractorLink {
    val b = ExtractorLinkBuilder(name, source, url).apply(builder)
    return ExtractorLink(
        source = b.source,
        name = b.name,
        url = b.url,
        referer = b.referer,
        quality = b.quality,
        isM3u8 = b.isM3u8,
        headers = b.headers,
        extractorData = b.extractorData
    )
}
