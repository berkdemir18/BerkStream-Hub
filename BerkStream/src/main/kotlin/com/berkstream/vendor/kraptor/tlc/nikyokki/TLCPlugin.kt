// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (TLC).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.kraptor.tlc.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.tlc.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class TLCPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TLC())
    }
}