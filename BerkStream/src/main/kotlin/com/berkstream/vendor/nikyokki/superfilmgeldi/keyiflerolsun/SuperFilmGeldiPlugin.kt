// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (SuperFilmGeldi).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.nikyokki.superfilmgeldi.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.superfilmgeldi.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class SuperFilmGeldiPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SuperFilmGeldi())
        registerExtractorAPI(MixPlayHD())
        registerExtractorAPI(MixTiger())
        registerExtractorAPI(VidmolyNet())
    }
}