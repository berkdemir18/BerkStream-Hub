// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (KoreanTurk).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.nikyokki.koreanturk.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.koreanturk.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class KoreanTurkPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KoreanTurk())
    }
}