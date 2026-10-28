// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (YoTurkish).
// Kaynak paket: com.kraptor -> com.berkstream.vendor.kraptor.yoturkish.kraptor
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.berkstream.vendor.kraptor.yoturkish.kraptor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class TukiPastiExtractor : ExtractorApi() {
    override val name = "TukiPasti"
    override val mainUrl = "https://tukipasti.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document

        val video = document.selectFirst("div#video_player")?.attr("data-hash") ?:""

        callback.invoke(
            newExtractorLink(
            name = this.name,
            source = this.name,
            url = video,
            type = ExtractorLinkType.M3U8, /* ExtractorLinkType.M3U8, ExtractorLinkType.VIDEO*/
            initializer = {
                this.referer = "${mainUrl}/"
            }
        ))
    }
}