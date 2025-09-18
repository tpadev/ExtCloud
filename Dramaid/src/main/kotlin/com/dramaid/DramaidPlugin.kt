
package com.dramaid

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.dramaid.extractors.Vanfem
import com.dramaid.extractors.Filelions
import com.dramaid.extractors.Gcam

@CloudstreamPlugin
class DramaidPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Dramaid())
    
        registerExtractorAPI(Vanfem())
        registerExtractorAPI(Filelions())
        registerExtractorAPI(Gcam())
    }
}