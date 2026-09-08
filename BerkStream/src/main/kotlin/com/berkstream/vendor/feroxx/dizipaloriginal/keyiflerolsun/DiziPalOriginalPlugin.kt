// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (DiziPalOriginal).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.dizipaloriginal.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.dizipaloriginal.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziPalOriginalPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziPalOriginal())
    }
}