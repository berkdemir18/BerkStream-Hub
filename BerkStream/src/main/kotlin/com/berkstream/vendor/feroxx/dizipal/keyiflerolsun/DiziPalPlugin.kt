// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (DiziPal).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.dizipal.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.dizipal.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziPalPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziPal())
        registerExtractorAPI(DizipalPlayer())
    }
}