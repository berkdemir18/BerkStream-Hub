// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (InatBox).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.inatbox.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.inatbox.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class InatBoxPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(InatBox())
        registerExtractorAPI(DiskYandexComTr())
        registerExtractorAPI(Vk())
        registerExtractorAPI(DzenRu())
        registerExtractorAPI(CDNJWPlayer())
    }
}