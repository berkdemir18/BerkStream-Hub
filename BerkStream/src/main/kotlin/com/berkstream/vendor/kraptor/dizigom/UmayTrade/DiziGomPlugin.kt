// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (DiziGom).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.dizigom.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.dizigom.UmayTrade
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziGomPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziGom())
    }
}
