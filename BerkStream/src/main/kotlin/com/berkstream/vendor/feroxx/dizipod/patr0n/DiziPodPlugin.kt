// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (DiziPod).
// Kaynak paket: com.patr0n -> com.berkstream.vendor.feroxx.dizipod.patr0n
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.dizipod.patr0n

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziPodPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziPod())
    }
}
