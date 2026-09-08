// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (BelgeselX).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.belgeselx.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.belgeselx.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class BelgeselXPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BelgeselX())
        registerExtractorAPI(Odnoklassniki())
    }
}