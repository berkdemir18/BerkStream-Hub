package com.berkstream

import android.content.Context
import android.widget.Toast
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.txt

/**
 * Oynatici kaynak menusune eklenen kendi dugmemiz.
 *
 * Basildiginda izlenen basligi BerkStream'in kendi gecmisine yaziyor; "Sana
 * Ozel" rafi bu kayitlardan besleniyor. Yani begendigini isaretledikce ana
 * sayfadaki oneriler sana yaklasiyor.
 */
class BerkStreamLikeAction : VideoClickAction() {
    override val name = txt("BerkStream: bunu beğendim")

    override fun shouldShow(context: Context?, video: ResultEpisode?) = video != null

    override suspend fun runAction(
        context: Context?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?,
    ) {
        val title = video.name?.takeIf { it.isNotBlank() } ?: video.headerName
        val store = BerkStreamSettings.store
        if (store == null || title.isBlank()) return
        runCatching {
            val previous = store.getString(WATCH_HISTORY_KEY, "").orEmpty()
                .split('\n').filter { it.isNotBlank() }
            // Basa yaziliyor: en son begenilen, oneriye en cok agirlik veren olsun.
            val updated = (listOf(title) + previous).distinct().take(60)
            store.edit().putString(WATCH_HISTORY_KEY, updated.joinToString("\n")).apply()
        }
        context?.let {
            uiThread { Toast.makeText(it, "BerkStream: önerilere eklendi", Toast.LENGTH_SHORT).show() }
        }
    }
}
