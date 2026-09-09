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

    private val playlists = mapOf(
        "tr" to listOf(
            "$mainUrl/countries/tr.m3u",
            "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_turkey.m3u8",
        ),
        "sports" to listOf(
            "$mainUrl/categories/sports.m3u",
            "$mainUrl/languages/tur.m3u",
        ),
    )

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
        val parsed = (playlists[source] ?: listOf("$mainUrl/countries/$source.m3u"))
            .flatMap { url -> runCatching { parse(app.get(url).text) }.getOrElse { emptyList() } }
            .distinctBy { it.url }
        if (parsed.isNotEmpty()) cache[source] = now to parsed
        return parsed
    }

    private suspend fun allChannels(): List<Channel> =
        (channels("tr") + channels("sports")).distinctBy { it.url }

    /**
     * Akis adresi dogrudan verilemiyor: CloudStream icerigi acarken adrese bakip
     * hangi saglayicinin isi oldugunu buluyor ve ham akis adresi (ornegin
     * hls.4utv.live) bizim mainUrl'imizle eslesmedigi icin "baglanti bulunamadi"
     * cikiyordu. Adres kendi alan adimizla sarmalanip [load] icinde cozuluyor.
     */
    private fun wrap(streamUrl: String) =
        "$mainUrl/watch?u=" + java.net.URLEncoder.encode(streamUrl, "UTF-8")

    private fun unwrap(url: String): String =
        if (url.contains("/watch?u=")) {
            runCatching { java.net.URLDecoder.decode(url.substringAfter("/watch?u="), "UTF-8") }
                .getOrDefault(url)
        } else {
            url
        }

    private fun Channel.toSearchResponse(): SearchResponse =
        newLiveSearchResponse(title, wrap(url), TvType.Live, false) {
            this.posterUrl = logo
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest) = run {
        val source = request.data.substringBefore('|')
        val group = request.data.substringAfter('|', "")
        val all = channels(source)
        val filtered = if (group.isBlank()) all else all.filter { it.group.equals(group, true) }
        // Turkiye spor rafi bos kalmasin: kategori etiketi eksikse ada bakiyoruz.
        val items = if (group == "Sports") {
            val byName = all.filter { channel ->
                val label = channel.title.lowercase()
                listOf(
                    "spor", "sport", "bein", "tjk", "fb tv", "gs tv", "bjk tv", "trt spor",
                    "a spor", "s sport", "htspor", "tivibu", "eurosport", "nba", "futbol",
                ).any(label::contains)
            }
            (filtered + byName).distinctBy { it.url }
        } else {
            filtered
        }
        val pageSize = 60
        val window = items.drop((page - 1) * pageSize).take(pageSize)
        newHomePageResponse(request, window.map { it.toSearchResponse() }, items.size > page * pageSize)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val needle = query.lowercase()
        return allChannels()
            .filter { it.title.lowercase().contains(needle) }
            .distinctBy { it.url }
            .take(40)
            .map { it.toSearchResponse() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val streamUrl = unwrap(url)
        val channel = allChannels().firstOrNull { it.url == streamUrl }
        return newLiveStreamLoadResponse(channel?.title ?: "Canlı yayın", url, streamUrl) {
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
