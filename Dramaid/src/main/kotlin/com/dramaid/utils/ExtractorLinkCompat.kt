package com.dramaid.utils

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities

class ExtractorLinkBuilder {
    var referer: String? = null
    var quality: Int = Qualities.Unknown.value
    var type: ExtractorLinkType? = null
    var headers: Map<String, String> = emptyMap()
    var extractorData: String? = null
}

@Suppress("DEPRECATION", "DEPRECATION_ERROR")
fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    build: ExtractorLinkBuilder.() -> Unit
): ExtractorLink {
    val b = ExtractorLinkBuilder().apply(build)
    return ExtractorLink(
        source = source,
        name = name,
        url = url,
        referer = b.referer ?: "",
        quality = b.quality,
        type = b.type,
        headers = b.headers,
        extractorData = b.extractorData
    )
}

