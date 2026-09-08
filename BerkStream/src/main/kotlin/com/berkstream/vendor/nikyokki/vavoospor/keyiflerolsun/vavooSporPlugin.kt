// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (vavooSpor).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.nikyokki.vavoospor.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.vavoospor.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class vavooSporPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(vavooSpor())
    }
}