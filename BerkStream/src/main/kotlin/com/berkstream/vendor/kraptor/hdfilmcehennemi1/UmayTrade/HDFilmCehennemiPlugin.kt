// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (HDFilmCehennemi1).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.hdfilmcehennemi1.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.hdfilmcehennemi1.UmayTrade

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class HDFilmCehennemiPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDFilmCehennemi())
    }
}