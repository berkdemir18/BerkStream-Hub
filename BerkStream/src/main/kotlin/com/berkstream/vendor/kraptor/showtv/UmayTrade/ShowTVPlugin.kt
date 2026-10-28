// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (Showtv).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.showtv.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.showtv.UmayTrade

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin

class ShowTvPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(ShowTv())
    }
}
