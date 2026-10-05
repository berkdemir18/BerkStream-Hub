package com.berkstream.extra

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * DramaDizilerim — Turkce altyazili/dublajli Asya dizileri.
 *
 * Kaynak: Emre-Kahveci/CloudStreamHub (GPL-3.0); depo ici `core` kutuphanesine
 * bagli oldugu icin buraya bagimsiz olarak tasindi.
 *
 * Upstream'den fark: bolum sayfasi dizinin BUTUN bolumlerinin embed.php
 * adresini tasiyor (o anki bolum `iframe`'de, digerleri `div[data-src]`'de).
 * Upstream hepsini topladigi icin 1. bolumu acinca 2, 3, 4... da "kaynak"
 * olarak geliyordu. Burada sadece istenen bolumun embed'i aliniyor.
 */
class DramaDizilerim : MainAPI() {
    override var mainUrl = "https://dramadizilerim.com"
    override var name = "DramaDizilerim"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/dizi" to "Tüm Diziler",
        mainUrl to "Trend Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val target = if (page <= 1) request.data else "${request.data}?page=$page"
        return newHomePageResponse(request.name, parseCards(app.get(target).document))
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val href = fixUrlNull(element.attr("href")) ?: return null
        if (!href.contains("/dizi/")) return null
        val img = element.selectFirst("img")
        val title = img?.attr("alt")?.trim()?.ifBlank { null }
            ?: element.selectFirst(".title, h3, h2, h4, span")?.text()?.trim()?.ifBlank { null }
            ?: element.text().trim().ifBlank { null }
            ?: return null
        val poster = fixUrlNull(
            img?.attr("src")?.ifBlank { null } ?: img?.attr("data-src")?.ifBlank { null }
        )
        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) { posterUrl = poster }
    }

    private fun parseCards(document: Document): List<SearchResponse> =
        document.select("a[href*='/dizi/']")
            .mapNotNull { toSearchResult(it) }
            .distinctBy { it.url.trimEnd('/') }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val term = URLEncoder.encode(query.trim(), "UTF-8")
        val target = if (page <= 1) "$mainUrl/search?q=$term" else "$mainUrl/search?q=$term&page=$page"
        return newSearchResponseList(parseCards(app.get(target).document), hasNext = false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.title().substringBefore("|").trim()
        val poster = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst("img.poster, div.dizi-poster img, img[src*='upload']")
                    ?.let { it.attr("src").ifBlank { it.attr("data-src") } }
        )
        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst("div.description, div.ozet, p.summary")?.text()?.trim()

        val episodes = mutableListOf<Episode>()
        val seen = mutableSetOf<String>()
        for (el in document.select("a[href*='/izle/']")) {
            val href = fixUrlNull(el.attr("href")) ?: continue
            if (!seen.add(href)) continue
            val season = seasonParam.find(href)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val episode = episodeParam.find(href)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val label = el.text().trim().takeIf { it.isNotBlank() && !it.contains("Şimdi İzle") }
            episodes.add(
                newEpisode(href) {
                    this.name = label ?: "$season. Sezon $episode. Bölüm"
                    this.season = season
                    this.episode = episode
                }
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            posterUrl = poster
            plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val document = app.get(data).document
        val wanted = episodeParam.find(data)?.groupValues?.getOrNull(1)

        val all = document.select("iframe[src*='embed'], [data-src*='embed.php']")
            .mapNotNull { fixUrlNull(it.attr("src").ifBlank { it.attr("data-src") }) }
            .distinct()
        val embeds = wanted
            ?.let { e -> all.filter { embedEpisode.find(it)?.groupValues?.getOrNull(1) == e } }
            ?.ifEmpty { null }
            ?: document.select("iframe[src]").mapNotNull { fixUrlNull(it.attr("src")) }

        var found = false
        embeds.amap { embedUrl ->
            runCatching {
                val html = app.get(embedUrl, headers = mapOf("Referer" to data)).text
                val video = sourceVar.find(html)?.groupValues?.getOrNull(1)
                if (!video.isNullOrBlank()) {
                    val isHls = video.contains(".m3u8")
                    callback(
                        newExtractorLink(
                            source = name,
                            name = if (isHls) "$name HLS" else "$name MP4",
                            url = video,
                            type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                        ) {
                            referer = embedUrl
                            quality = qualityOf(video)
                        }
                    )
                    found = true
                }
                trackSrc.findAll(html).forEach { match ->
                    fixUrlNull(match.groupValues[1])?.let { subtitleCallback(newSubtitleFile("tr", it)) }
                }
            }
        }
        return found
    }

    private fun qualityOf(url: String): Int = when {
        url.contains("2160p") -> Qualities.P2160.value
        url.contains("1080p") -> Qualities.P1080.value
        url.contains("720p") -> Qualities.P720.value
        url.contains("480p") -> Qualities.P480.value
        else -> Qualities.Unknown.value
    }

    private companion object {
        val seasonParam = Regex("""[?&]s=(\d+)""")
        val episodeParam = Regex("""[?&]e=(\d+)""")
        val embedEpisode = Regex("""[?&]episode=(\d+)""")
        val sourceVar = Regex("""let\s+source\s*=\s*["']([^"']+)["']""")
        val trackSrc = Regex("""<track[^>]+src=["']([^"']+)["']""")
    }
}
