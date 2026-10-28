// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (Streamed).
// Kaynak paket: (varsayilan paket) -> com.berkstream.vendor.kraptor.streamed.kraptor
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.streamed.kraptor
// Plugin sınıfı için güncellenmiş kod
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.berkstream.vendor.kraptor.streamed.kraptor.EmbedSporty
import com.berkstream.vendor.kraptor.streamed.kraptor.EmbedStreams
import com.berkstream.vendor.kraptor.streamed.kraptor.Streamed

class StreamedPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Streamed())
        registerExtractorAPI(EmbedStreams(context))
        registerExtractorAPI(EmbedSporty(context))
    }
}