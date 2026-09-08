// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (HDFilmIzle).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.hdfilmizle.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.hdfilmizle.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class HDFilmIzlePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDFilmIzle())
        registerExtractorAPI(VidRameExtractor())
    }
}