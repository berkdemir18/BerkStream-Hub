// BerkStream tarafindan ReCloudStream Official Extensions deposundan derlendi (TwitchProvider).
// Kaynak paket: recloudstream -> com.berkstream.vendor.official.twitchprovider.recloudstream
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.official.twitchprovider.recloudstream

import com.lagradost.cloudstream3.plugins.BasePlugin

class TwitchPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(TwitchProvider())
        registerExtractorAPI(TwitchProvider.TwitchExtractor())
    }
}