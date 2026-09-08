// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (DiziKorea).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.dizikorea.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.dizikorea.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziKoreaPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziKorea())
        registerExtractorAPI(VideoSeyred())
		registerExtractorAPI(PlayerKorea())
    }
}