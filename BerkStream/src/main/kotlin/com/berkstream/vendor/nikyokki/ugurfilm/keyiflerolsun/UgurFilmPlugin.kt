// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (UgurFilm).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.nikyokki.ugurfilm.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.ugurfilm.keyiflerolsun

import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

class UgurFilmPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(UgurFilm())
        registerExtractorAPI(MailRu())
        registerExtractorAPI(Odnoklassniki())
    }
}