package com.berkstream

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * Tek eklenti girisi.
 *
 * BerkStream kendi saglayicisinin yaninda, gomulu tum kaynaklari da bu tek
 * .cs3 dosyasi uzerinden kaydeder. Kullanici tek eklenti kurar, butun kaynaklar
 * acilir. Kaynak listesi `scripts/vendor-sources.mjs` tarafindan uretilen
 * [VENDORED_SOURCES] icindedir.
 */
@CloudstreamPlugin
class BerkStreamPlugin : Plugin() {
    private val loadedSources = mutableListOf<BasePlugin>()

    override fun load(context: Context) {
        registerMainAPI(BerkStreamProvider())
        loadPltEngine()
        loadVendoredSources(context)
    }

    /**
     * PLT Stream kapali kaynak oldugu icin kodu derlenemiyor; CI derlemesi onun
     * dex'ini bu pakete ekliyor ve motor buradan yansimayla ayaga kaldiriliyor.
     */
    private fun loadPltEngine() {
        runCatching {
            val engine = Class.forName("com.pltmustafa.pltstream.PLTStream")
                .getDeclaredConstructor().newInstance() as MainAPI
            engine.name = "BerkStream Kaynakları"
            registerMainAPI(engine)
        }.onFailure { Log.w(TAG, "PLT motoru yuklenemedi", it) }
    }

    /**
     * Her kaynak kendi basina yuklenir: biri patlarsa digerleri etkilenmez.
     */
    private fun loadVendoredSources(context: Context) {
        var ok = 0
        for (source in VENDORED_SOURCES) {
            runCatching {
                val plugin = source.create()
                // Kaldirma ve kaynak esleme icin alt eklentiler de bu .cs3'e baglanir.
                plugin.filename = this.filename
                if (plugin is Plugin) {
                    plugin.resources = this.resources
                    plugin.load(context)
                } else {
                    plugin.load()
                }
                loadedSources.add(plugin)
                ok++
            }.onFailure { Log.e(TAG, "Kaynak yuklenemedi: ${source.id} (${source.origin})", it) }
        }
        Log.i(TAG, "BerkStream: $ok/${VENDORED_SOURCES.size} gomulu kaynak yuklendi")
    }

    override fun beforeUnload() {
        for (plugin in loadedSources) {
            runCatching { plugin.beforeUnload() }
        }
        loadedSources.clear()
    }

    companion object {
        private const val TAG = "BerkStream"
    }
}
