// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (HDFilmSitesi).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.hdfilmsitesi.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.hdfilmsitesi.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class HDFilmSitesiPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDFilmSitesi())
    }
}