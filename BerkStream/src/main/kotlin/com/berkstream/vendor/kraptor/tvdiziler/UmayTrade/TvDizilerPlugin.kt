// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (TvDiziler).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.tvdiziler.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.tvdiziler.UmayTrade

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class TvDizilerPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TvDiziler())
    }
}
