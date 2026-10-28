// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (BirAsyaDizi).
// Kaynak paket: com.kraptor -> com.berkstream.vendor.kraptor.birasyadizi.kraptor
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.berkstream.vendor.kraptor.birasyadizi.kraptor

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class BirAsyaDiziPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BirAsyaDizi())
    }
}