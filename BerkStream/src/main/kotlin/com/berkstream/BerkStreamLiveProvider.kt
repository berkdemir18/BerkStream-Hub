package com.berkstream

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.concurrent.ConcurrentHashMap

/**
 * BerkStream'in kendi canli yayin saglayicisi.
 *
 * Neden yazildi: pakete gomulu canli kaynaklarin spor tarafi calismiyordu.
 * Olculdu — vavooSpor'un listesinden hicbir akis cikarilamadi, iptv-org'un
 * listelerinde ise denenen 6 akistan 5'i cevap verdi. iptv-org acik kaynakli,
 * anahtarsiz ve serbestce yayinlanan kanallari topluyor.
 *
 * Listeler M3U; ayristirma burada yapiliyor, akis adresi dogrudan oynaticiya
 * veriliyor.
 */
class BerkStreamLiveProvider : MainAPI() {
    override var name = "BerkStream Canlı"
    override var mainUrl = "https://iptv-org.github.io/iptv"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "tr|Sports" to "⚽  SPOR • TÜRKİYE",
        "sports|" to "🌍  SPOR • DÜNYA",
        "tr|" to "🇹🇷  TÜRKİYE • TÜM KANALLAR",
        "tr|News" to "📰  HABER",
        "tr|Movies" to "🎬  SİNEMA",
        "tr|Entertainment" to "🎭  EĞLENCE",
        "tr|Music" to "🎵  MÜZİK",
        "tr|Kids" to "🧒  ÇOCUK",
        "tr|Documentary" to "📖  BELGESEL",
        "tr|Religious" to "🕌  DİNİ",
    )

    private data class Channel(
        val title: String,
        val url: String,
        val logo: String?,
        val group: String,
    )

    private val cache = ConcurrentHashMap<String, Pair<Long, List<Channel>>>()
    private val cacheMs = 30 * 60 * 1000L

    private fun playlistUrl(source: String) = when (source) {
        "sports" -> "$mainUrl/categories/sports.m3u"
        else -> "$mainUrl/countries/$source.m3u"
    }

    /** Basit M3U ayristirici: #EXTINF satiri + hemen ardindaki adres. */
    private fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = text.lineSequence().map(String::trim).filter { it.isNotEmpty() }.toList()
        for (index in lines.indices) {
            val line = lines[index]
            if (!line.startsWith("#EXTINF")) continue
            val streamUrl = lines.drop(index + 1).firstOrNull { !it.startsWith("#") } ?: continue
            if (!streamUrl.startsWith("http")) continue
            val title = line.substringAfter(',', "").trim()
            if (title.isBlank()) continue
            channels += Channel(
                title = title,
                url = streamUrl,
                logo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.getOrNull(1)
                    ?.takeIf { it.isNotBlank() },
                group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.getOrNull(1)
                    .orEmpty(),
            )
        }
        return channels
    }

    private suspend fun channels(source: String): List<Channel> {
        val now = System.currentTimeMillis()
        cache[source]?.takeIf { now - it.first < cacheMs }?.let { return it.second }
        val parsed = runCatching { parse(app.get(playlistUrl(source)).text) }.getOrElse { emptyList() }
        if (parsed.isNotEmpty()) cache[source] = now to parsed
        return parsed
    }

    private fun Channel.toSearchResponse(): SearchResponse =
        newLiveSearchResponse(title, url, TvType.Live, false) {
            this.posterUrl = logo
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest) = run {
        val source = request.data.substringBefore('|')
        val group = request.data.substringAfter('|', "")
        val all = channels(source)
        val filtered = if (group.isBlank()) all else all.filter { it.group.equals(group, true) }
        // Turkiye spor rafi bos kalmasin: kategori etiketi eksikse ada bakiyoruz.
        val items = filtered.ifEmpty {
            if (group == "Sports") {
                all.filter { channel ->
                    val label = channel.title.lowercase()
                    listOf("spor", "sport", "bein", "tjk", "fb tv", "gs tv", "bjk").any(label::contains)
                }
            } else {
                emptyList()
            }
        }
        val pageSize = 60
        val window = items.drop((page - 1) * pageSize).take(pageSize)
        newHomePageResponse(request, window.map { it.toSearchResponse() }, items.size > page * pageSize)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val needle = query.lowercase()
        return (channels("tr") + channels("sports"))
            .filter { it.title.lowercase().contains(needle) }
            .distinctBy { it.url }
            .take(40)
            .map { it.toSearchResponse() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val channel = (channels("tr") + channels("sports")).firstOrNull { it.url == url }
        return newLiveStreamLoadResponse(channel?.title ?: "Canlı yayın", url, url) {
            this.posterUrl = channel?.logo
            this.plot = channel?.group?.takeIf { it.isNotBlank() }?.let { "Kategori: $it" }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        if (!data.startsWith("http")) return false
        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = data,
                type = if (data.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
            ),
        )
        return true
    }
}
