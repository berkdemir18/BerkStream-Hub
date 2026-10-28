// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (YoTurkish).
// Kaynak paket: com.kraptor -> com.berkstream.vendor.kraptor.yoturkish.kraptor
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @Kraptor123 tarafından | @CS-Karma için yazılmıştır.
package com.berkstream.vendor.kraptor.yoturkish.kraptor

import com.lagradost.cloudstream3.plugins.Plugin

class YoTurkishPlugin: Plugin() {
    override fun load() {
        registerMainAPI(YoTurkish())
        registerExtractorAPI(EngifuosiExtractor())
        registerExtractorAPI(TukiPastiExtractor())
    }
}