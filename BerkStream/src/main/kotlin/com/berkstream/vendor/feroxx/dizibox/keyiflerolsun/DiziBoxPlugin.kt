// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (DiziBox).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.dizibox.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.dizibox.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziBoxPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziBox())
    }
}