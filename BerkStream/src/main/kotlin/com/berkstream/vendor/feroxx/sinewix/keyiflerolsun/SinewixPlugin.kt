// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (Sinewix).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.sinewix.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.sinewix.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class SinewixPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added here in this manner:
        registerMainAPI(Sinewix())
    }
}
