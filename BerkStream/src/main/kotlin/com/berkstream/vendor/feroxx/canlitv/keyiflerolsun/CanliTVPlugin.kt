// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (CanliTV).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.canlitv.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.canlitv.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class CanliTVPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CanliTV())
    }
}