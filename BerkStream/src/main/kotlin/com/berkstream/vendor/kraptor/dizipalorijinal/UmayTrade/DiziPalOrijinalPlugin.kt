// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (DiziPalOrijinal).
// Kaynak paket: com.UmayTrade -> com.berkstream.vendor.kraptor.dizipalorijinal.UmayTrade
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.dizipalorijinal.UmayTrade

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class DiziPalOrijinalPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziPalOrijinal())
        registerExtractorAPI(ContentX())
        registerExtractorAPI(Hotlinger())
        registerExtractorAPI(RapidVid())
        registerExtractorAPI(TRsTX())
        registerExtractorAPI(VidMoxy())
        registerExtractorAPI(Sobreatsesuyp())
        registerExtractorAPI(TurboImgz())
        registerExtractorAPI(TurkeyPlayer())
        registerExtractorAPI(Hotlinger())
        registerExtractorAPI(FourCX())
        registerExtractorAPI(PlayRu())
        registerExtractorAPI(FourPlayRu())
        registerExtractorAPI(FourPichive())
        registerExtractorAPI(Pichive())
        registerExtractorAPI(FourDplayer())
        registerExtractorAPI(SNDplayer())
        registerExtractorAPI(ORGDplayer())
        registerExtractorAPI(Dplayer())
    }
}