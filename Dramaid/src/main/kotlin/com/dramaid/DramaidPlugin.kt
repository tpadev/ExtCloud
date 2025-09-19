package com.dramaid

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

// Import extractor2 kamu
import com.dramaid.extractors.GdriveWebId
import com.dramaid.extractors.Vanfem
import com.dramaid.extractors.Filelions
import com.dramaid.extractors.Gcam

@CloudstreamPlugin
class DramaidPlugin : Plugin() {
    override fun load(context: Context) {
        // Provider utama
        registerMainAPI(Dramaid())

        // **Wajib** daftar extractor agar dipanggil loadExtractor()
        registerExtractorAPI(GdriveWebId())  // <-- Tambahkan ini

        // Extractor lain yang kamu gunakan
        registerExtractorAPI(Vanfem())
        registerExtractorAPI(Filelions())
        registerExtractorAPI(Gcam())
    }
}