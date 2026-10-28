// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (LoveFilm).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.lovefilm.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.lovefilm.UmayTrade

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin

class LoveFilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(LoveFilm())
        registerExtractorAPI(VidMolyExtractor())
    }
}
