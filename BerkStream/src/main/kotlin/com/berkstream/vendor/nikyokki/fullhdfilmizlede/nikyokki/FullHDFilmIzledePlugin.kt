// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (FullHDFilmİzlede).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.fullhdfilmizlede.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.fullhdfilmizlede.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class FullHDFilmIzledePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FullHDFilmIzlede())
    }
}