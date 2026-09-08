// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (WebdramaTurkey2).
// Kaynak paket: com.patron -> com.berkstream.vendor.feroxx.webdramaturkey2.patron
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.webdramaturkey2.patron

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin

class WebdramaTurkey2Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(WebdramaTurkey2())
        registerExtractorAPI(WebDramaTurkeyExtractor())
        registerExtractorAPI(VkExtractor())
        registerExtractorAPI(VkCom())
        registerExtractorAPI(Abstream())
    }
}
