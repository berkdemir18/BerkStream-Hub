// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (DiziLife).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.dizilife.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.dizilife.UmayTrade

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin

class DiziLifePlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziLife())
    }
}
