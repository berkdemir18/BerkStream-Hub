// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (KanalD).
// Kaynak paket: com.kerimmkirac -> com.berkstream.vendor.kraptor.kanald.kerimmkirac
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.kanald.kerimmkirac



import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class KanalDPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KanalD())
    }
}
