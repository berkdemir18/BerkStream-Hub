// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (KultFilmler).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.kultfilmler.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.kultfilmler.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class KultFilmlerPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KultFilmler())
        registerExtractorAPI(YildizKisaFilm())
    }
}