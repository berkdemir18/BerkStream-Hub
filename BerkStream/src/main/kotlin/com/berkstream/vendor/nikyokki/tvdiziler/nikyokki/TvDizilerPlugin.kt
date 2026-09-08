// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (TvDiziler).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.tvdiziler.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.tvdiziler.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class TvDizilerPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TvDiziler())
        registerExtractorAPI(TvDizilerOynat())
    }
}