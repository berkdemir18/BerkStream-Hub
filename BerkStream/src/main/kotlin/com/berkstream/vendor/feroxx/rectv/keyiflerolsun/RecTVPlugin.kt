// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (RecTV).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.rectv.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.rectv.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class RecTVPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(RecTV())
    }
}