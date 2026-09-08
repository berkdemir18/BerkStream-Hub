// BerkStream tarafindan ReCloudStream Official Extensions deposundan derlendi (YoutubeProvider).
// Kaynak paket: recloudstream -> com.berkstream.vendor.official.youtubeprovider.recloudstream
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.official.youtubeprovider.recloudstream

import com.lagradost.cloudstream3.plugins.BasePlugin

class YoutubePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(YoutubeProvider())
    }
}