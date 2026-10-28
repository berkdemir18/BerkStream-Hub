// BerkStream tarafindan NeO Turkish Providers deposundan derlendi (KoreanTurk).
// Kaynak paket: com.neowatch -> com.berkstream.vendor.neowatch.koreanturk.neowatch
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.neowatch.koreanturk.neowatch

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class KoreanTurkPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KoreanTurk())
    }
}