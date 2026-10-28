// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (Sinefy).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.sinefy.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.

package com.berkstream.vendor.kraptor.sinefy.UmayTrade

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class SinefyPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Sinefy())
    }
}