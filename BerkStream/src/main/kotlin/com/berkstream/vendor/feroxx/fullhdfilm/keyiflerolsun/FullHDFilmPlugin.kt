// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (FullHDFilm).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.fullhdfilm.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.fullhdfilm.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class FullHDFilmPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FullHDFilm())
        registerExtractorAPI(YildizKisaFilm())
    }
}