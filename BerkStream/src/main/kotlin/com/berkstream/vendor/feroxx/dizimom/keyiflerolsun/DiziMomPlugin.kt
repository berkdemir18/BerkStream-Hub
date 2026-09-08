// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (DiziMom).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.dizimom.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.dizimom.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziMomPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziMom())
        registerExtractorAPI(HDMomPlayer())
        registerExtractorAPI(HDPlayerSystem())
        registerExtractorAPI(VideoSeyred())
        registerExtractorAPI(PeaceMakerst())
        registerExtractorAPI(HDStreamAble())
    }
}