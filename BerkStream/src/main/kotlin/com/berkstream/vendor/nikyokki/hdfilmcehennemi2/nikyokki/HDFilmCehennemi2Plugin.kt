// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (HDFilmCehennemi2).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.hdfilmcehennemi2.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.hdfilmcehennemi2.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class HDFilmCehennemi2Plugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDFilmCehennemi2())
    }
}