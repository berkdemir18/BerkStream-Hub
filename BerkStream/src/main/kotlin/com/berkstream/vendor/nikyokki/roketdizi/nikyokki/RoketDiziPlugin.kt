// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (RoketDizi).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.roketdizi.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.roketdizi.nikyokki

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class RoketDiziPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(RoketDizi())
        registerExtractorAPI(ContentXExtractor())
        registerExtractorAPI(Hotlinger())
        registerExtractorAPI(FourCX())
        registerExtractorAPI(PlayRu())
        registerExtractorAPI(FourPlayRu())
        registerExtractorAPI(FourPichive())
        registerExtractorAPI(Pichive())
    }
}