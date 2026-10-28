// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (DocumentaryArea).
// Kaynak paket: com.byayzen -> com.berkstream.vendor.kraptor.documentaryarea.byayzen
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.berkstream.vendor.kraptor.documentaryarea.byayzen

import com.lagradost.cloudstream3.plugins.Plugin

class DocumentaryAreaPlugin: Plugin() {
    override fun load() {
        registerMainAPI(DocumentaryArea())
    }
}