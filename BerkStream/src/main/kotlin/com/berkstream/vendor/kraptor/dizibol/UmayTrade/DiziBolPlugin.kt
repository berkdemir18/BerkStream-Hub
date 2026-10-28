// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (DiziBol).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.dizibol.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.dizibol.UmayTrade

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin

class DiziBolPlugin: Plugin() {

    override fun load(context: Context) {
        registerMainAPI(DiziBol())
    }
}
