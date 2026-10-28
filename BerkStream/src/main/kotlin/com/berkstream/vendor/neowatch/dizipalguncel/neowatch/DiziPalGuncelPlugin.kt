// BerkStream tarafindan NeO Turkish Providers deposundan derlendi (DiziPalGuncel).
// Kaynak paket: com.neowatch -> com.berkstream.vendor.neowatch.dizipalguncel.neowatch
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.neowatch.dizipalguncel.neowatch

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziPalGuncelPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziPalGuncel())
        registerExtractorAPI(ContentXGenel())
    }
}
