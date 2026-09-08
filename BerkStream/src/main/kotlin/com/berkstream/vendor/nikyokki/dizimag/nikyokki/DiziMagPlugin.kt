// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (DiziMag).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.dizimag.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.dizimag.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziMagPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziMag())
    }
}