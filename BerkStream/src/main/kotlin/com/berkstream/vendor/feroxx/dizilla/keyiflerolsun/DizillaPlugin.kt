// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (Dizilla).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.dizilla.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.dizilla.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DizillaPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Dizilla())
        registerExtractorAPI(ContentX())
        registerExtractorAPI(Hotlinger())
        registerExtractorAPI(FourCX())
        registerExtractorAPI(PlayRu())
        registerExtractorAPI(FourPlayRu())
        registerExtractorAPI(FourPichive())
        registerExtractorAPI(Pichive())
        registerExtractorAPI(SNplayer())
    }
}