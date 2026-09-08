// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (SinemaCX).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.sinemacx.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.sinemacx.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class SinemaCXPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SinemaCX())
    }
}