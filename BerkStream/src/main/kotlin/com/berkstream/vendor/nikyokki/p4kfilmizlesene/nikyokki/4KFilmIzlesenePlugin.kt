// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (4KFilmIzlesene).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.p4kfilmizlesene.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.p4kfilmizlesene.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class `4KFilmIzlesenePlugin`: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(`4KFilmIzlesene`())
    }
}