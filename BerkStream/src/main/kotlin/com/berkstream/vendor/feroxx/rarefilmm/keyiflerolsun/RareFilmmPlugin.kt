// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (RareFilmm).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.rarefilmm.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.rarefilmm.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class RareFilmmPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(RareFilmm())
        registerExtractorAPI(Odnoklassniki())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(OkRuHTTP())
    }
}