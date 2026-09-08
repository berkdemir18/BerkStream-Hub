// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (powerSinema).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.nikyokki.powersinema.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.powersinema.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class powerSinemaPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(powerSinema())
    }
}