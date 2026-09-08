// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (SelcukFlix).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.selcukflix.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.selcukflix.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class SelcukFlixPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SelcukFlix())
    }
}
