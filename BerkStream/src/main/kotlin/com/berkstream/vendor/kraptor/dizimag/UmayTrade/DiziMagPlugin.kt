// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (DiziMag).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.dizimag.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.dizimag.UmayTrade

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin

class DiziMagPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziMag())
    }
}
