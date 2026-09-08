package com.berkstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.MainAPI

@CloudstreamPlugin
class BerkStreamPlugin : Plugin() {
    override fun load(context: Context) {
        runCatching {
            val engine = Class.forName("com.pltmustafa.pltstream.PLTStream")
                .getDeclaredConstructor().newInstance() as MainAPI
            engine.name = "BerkStream Kaynakları"
            registerMainAPI(engine)
        }
        registerMainAPI(BerkStreamProvider())
    }
}
