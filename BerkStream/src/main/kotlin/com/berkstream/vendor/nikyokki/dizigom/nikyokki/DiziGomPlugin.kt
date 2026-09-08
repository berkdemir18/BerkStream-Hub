// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (DiziGom).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.dizigom.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.dizigom.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziGomPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziGom())
    }
}