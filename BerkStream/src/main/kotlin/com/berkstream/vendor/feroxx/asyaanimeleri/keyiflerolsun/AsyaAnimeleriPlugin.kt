// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (AsyaAnimeleri).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.asyaanimeleri.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.asyaanimeleri.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class AsyaAnimeleriPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AsyaAnimeleri())
    }
}
