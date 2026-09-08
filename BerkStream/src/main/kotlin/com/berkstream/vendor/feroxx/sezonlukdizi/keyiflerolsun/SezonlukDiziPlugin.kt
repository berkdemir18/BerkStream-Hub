// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (SezonlukDizi).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.sezonlukdizi.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.sezonlukdizi.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class SezonlukDiziPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SezonlukDizi())
    }
}