// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (FilmMakinesi).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.filmmakinesi.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.filmmakinesi.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class FilmMakinesiPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmMakinesi())
        registerExtractorAPI(CloseLoad())
    }
}