// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (HDFilmCehennemi).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.hdfilmcehennemi.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.hdfilmcehennemi.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class HDFilmCehennemiPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDFilmCehennemi())
    }
}