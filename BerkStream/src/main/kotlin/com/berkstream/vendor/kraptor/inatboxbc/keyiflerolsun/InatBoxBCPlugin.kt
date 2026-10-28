// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (inatbox-bc).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.kraptor.inatboxbc.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.inatboxbc.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class InatBoxBCPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(InatBoxBC())
        registerExtractorAPI(DiskYandexComTr())
        registerExtractorAPI(Vk())
        registerExtractorAPI(DzenRu())
        registerExtractorAPI(CDNJWPlayer())
    }
}
