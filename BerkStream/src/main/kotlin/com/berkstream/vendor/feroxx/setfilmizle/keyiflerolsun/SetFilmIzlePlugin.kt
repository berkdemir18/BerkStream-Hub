// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (SetFilmIzle).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.setfilmizle.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.setfilmizle.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class SetFilmIzlePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SetFilmIzle())
        registerExtractorAPI(SetPlay())
        registerExtractorAPI(FastPlay())
    }
}