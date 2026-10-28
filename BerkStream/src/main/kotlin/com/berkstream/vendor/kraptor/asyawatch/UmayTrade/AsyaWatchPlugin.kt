// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (AsyaWatch).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.asyawatch.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.asyawatch.UmayTrade

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class AsyaWatchPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AsyaWatch())
    }
}
