// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (HdFilmCehennemi2).
// Kaynak paket: com.kraptor -> com.berkstream.vendor.kraptor.hdfilmcehennemi2.kraptor
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.berkstream.vendor.kraptor.hdfilmcehennemi2.kraptor

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class HdFilmCehennemi2Plugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HdFilmCehennemi2())
        registerExtractorAPI(CloseLoad())
    }
}