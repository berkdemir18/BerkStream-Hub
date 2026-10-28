// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (FilmEkseni).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.filmekseni.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.filmekseni.UmayTrade

import android.content.Context
import com.berkstream.vendor.kraptor.filmekseni.UmayTrade.extractors.EksenLoadExtractor
import com.berkstream.vendor.kraptor.filmekseni.UmayTrade.extractors.VidMolyExtractor
import com.lagradost.cloudstream3.plugins.Plugin

class FilmEkseniPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmEkseni())

        registerExtractorAPI(EksenLoadExtractor())
        registerExtractorAPI(VidMolyExtractor())
    }
}
