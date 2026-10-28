// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (Supercartoons).
// Kaynak paket: com.byayzen -> com.berkstream.vendor.kraptor.supercartoons.byayzen
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.
package com.berkstream.vendor.kraptor.supercartoons.byayzen

import com.lagradost.cloudstream3.plugins.Plugin

class SuperCartoonsPlugin: Plugin() {
    override fun load() {
        registerMainAPI(SuperCartoons())
    }
}