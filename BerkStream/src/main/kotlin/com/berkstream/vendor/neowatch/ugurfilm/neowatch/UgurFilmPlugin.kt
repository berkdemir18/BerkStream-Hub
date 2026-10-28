// BerkStream tarafindan NeO Turkish Providers deposundan derlendi (UgurFilm).
// Kaynak paket: com.neowatch -> com.berkstream.vendor.neowatch.ugurfilm.neowatch
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.neowatch.ugurfilm.neowatch

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class UgurFilmPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(UgurFilm())
        registerExtractorAPI(MailRu())
        registerExtractorAPI(Odnoklassniki())
    }
}