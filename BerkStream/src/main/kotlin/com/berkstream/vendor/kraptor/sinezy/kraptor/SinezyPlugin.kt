// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (Sinezy).
// Kaynak paket: com.kraptor -> com.berkstream.vendor.kraptor.sinezy.kraptor
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.berkstream.vendor.kraptor.sinezy.kraptor

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class SinezyPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Sinezy())
        registerExtractorAPI(PlayerFilmIzle())
    }
}