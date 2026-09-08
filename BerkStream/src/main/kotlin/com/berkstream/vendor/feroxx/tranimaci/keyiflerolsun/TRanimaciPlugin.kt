// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (TRanimaci).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.tranimaci.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.tranimaci.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class TRanimaciPlugin: Plugin() {
    companion object {
        var pluginContext: Context? = null
    }
    override fun load(context: Context) {
        pluginContext = context
        registerMainAPI(TRanimaci())
    }
}