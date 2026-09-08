package com.berkstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class BerkStreamPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BerkStreamProvider())
    }
}
