// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (TurkAnime).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.turkanime.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.turkanime.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class TurkAnimePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TurkAnime())
    }
}