// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (DiziYo).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.diziyo.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.diziyo.UmayTrade

import android.content.Context
import com.berkstream.vendor.kraptor.diziyo.UmayTrade.extractors.VidMolyExtractor
import com.lagradost.cloudstream3.plugins.Plugin

class DiziYoPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziYo())

        registerExtractorAPI(VidMolyExtractor())
    }
}
