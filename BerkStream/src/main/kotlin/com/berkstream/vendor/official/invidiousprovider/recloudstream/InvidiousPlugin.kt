// BerkStream tarafindan ReCloudStream Official Extensions deposundan derlendi (InvidiousProvider).
// Kaynak paket: recloudstream -> com.berkstream.vendor.official.invidiousprovider.recloudstream
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.official.invidiousprovider.recloudstream

import com.lagradost.cloudstream3.plugins.BasePlugin

class InvidiousPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(InvidiousProvider())
    }
}