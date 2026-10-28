// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (Filmzal).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.filmzal.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.filmzal.UmayTrade

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class FilmzalPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Filmzal())
    }
}
