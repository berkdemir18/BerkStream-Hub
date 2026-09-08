// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (YabanciDizi).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.yabancidizi.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.yabancidizi.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class YabanciDiziPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(YabanciDizi())
    }
}