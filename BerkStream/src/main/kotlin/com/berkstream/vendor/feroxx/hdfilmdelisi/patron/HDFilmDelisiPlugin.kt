// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (HDFilmDelisi).
// Kaynak paket: com.patron -> com.berkstream.vendor.feroxx.hdfilmdelisi.patron
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.hdfilmdelisi.patron

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin

class HDFilmDelisiPlugin : Plugin() {
    override fun load(context: Context) {
        val prefs = context.getSharedPreferences("DomainListesi", Context.MODE_PRIVATE)
        HDFilmDelisiHelper.setup(context)
        registerMainAPI(HDFilmDelisi(prefs))
        registerExtractorAPI(VidMody())
    }
}
