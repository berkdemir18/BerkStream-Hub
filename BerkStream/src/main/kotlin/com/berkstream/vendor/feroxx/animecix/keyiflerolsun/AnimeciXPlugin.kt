// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (AnimeciX).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.animecix.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.animecix.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class AnimeciXPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeciX())
        registerExtractorAPI(TauVideo())
    }
}
