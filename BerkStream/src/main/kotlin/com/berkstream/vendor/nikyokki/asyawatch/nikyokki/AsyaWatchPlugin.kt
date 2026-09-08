// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (AsyaWatch).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.asyawatch.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.asyawatch.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class AsyaWatchPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AsyaWatch())
    }
}
