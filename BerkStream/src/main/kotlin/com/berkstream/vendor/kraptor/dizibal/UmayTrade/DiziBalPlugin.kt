// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (DiziBal).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.dizibal.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.dizibal.UmayTrade

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin

class DiziBalPlugin: Plugin() {

    override fun load(context: Context) {
        registerMainAPI(DiziBal())
    }
}
