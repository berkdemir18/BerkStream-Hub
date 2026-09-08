// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (TRasyalog).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.trasyalog.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.trasyalog.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class TRasyalogPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TRasyalog())
    }
}