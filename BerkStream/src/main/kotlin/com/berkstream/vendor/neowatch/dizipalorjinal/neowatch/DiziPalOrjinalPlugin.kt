// BerkStream tarafindan NeO Turkish Providers deposundan derlendi (DiziPalOrjinal).
// Kaynak paket: com.neowatch -> com.berkstream.vendor.neowatch.dizipalorjinal.neowatch
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.neowatch.dizipalorjinal.neowatch

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziPalOrjinalPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziPalOrjinal())
        registerExtractorAPI(VideoPlays())
        registerExtractorAPI(StreamCore())
    }
}
