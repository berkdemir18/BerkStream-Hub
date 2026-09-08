// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (JetFilmizle).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.jetfilmizle.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.jetfilmizle.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class JetFilmizlePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(JetFilmizle())
        registerExtractorAPI(PixelDrain())
    }
}