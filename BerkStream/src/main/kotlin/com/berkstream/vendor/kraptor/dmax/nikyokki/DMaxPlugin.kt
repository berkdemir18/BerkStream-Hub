// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (DMax).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.kraptor.dmax.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.dmax.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DMaxPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DMax())
    }
}