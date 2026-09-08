// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (powerDizi).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.nikyokki.powerdizi.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.powerdizi.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class powerDiziPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(powerDizi())
    }
}