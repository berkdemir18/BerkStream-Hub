// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (rectv-bc).
// Kaynak paket: com.droctagon -> com.berkstream.vendor.kraptor.rectvbc.droctagon
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.rectvbc.droctagon

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class RecTVBCPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(RecTVBC())
    }
}
