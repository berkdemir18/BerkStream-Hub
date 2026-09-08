// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (TLCtr).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.tlctr.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.tlctr.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class TlctrPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Tlctr())
    }
}