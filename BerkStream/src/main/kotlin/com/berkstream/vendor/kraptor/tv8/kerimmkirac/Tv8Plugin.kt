// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (Tv8).
// Kaynak paket: com.kerimmkirac -> com.berkstream.vendor.kraptor.tv8.kerimmkirac
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.tv8.kerimmkirac

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class Tv8Plugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Tv8())
    }
}