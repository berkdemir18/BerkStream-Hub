// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (WFilmİzle).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.wfilmizle.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.wfilmizle.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class WFilmIzlePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(WFilmIzle())
    }
}