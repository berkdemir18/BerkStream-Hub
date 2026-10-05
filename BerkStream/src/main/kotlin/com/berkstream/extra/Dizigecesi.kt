package com.berkstream.extra

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
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Dizigecesi — yabanci dizi ve film.
 *
 * Kaynak: Emre-Kahveci/CloudStreamHub (GPL-3.0). Oradaki modul depo ici bir
 * `core` kutuphanesine bagli oldugu icin vendor betigi tasiyamiyordu; o uc
 * yardimci (paralel cozucu, tekillestirme, akis dogrulama) burada CloudStream'in
 * kendi araclariyla yazildi.
 *
 * Upstream'den fark: arama `/?s=` ile yapiliyordu ve site bu parametreyi
 * yok sayip her aramada ana sayfayi donduruyordu (2026-10-05 olcumu: "ask"
 * aramasi Spartacus/Dexter getiriyordu). Gercek arama `/arama/{kelime}`.
 */
class Dizigecesi : MainAPI() {
    override var mainUrl = "https://dizigecesi.com"
    override var name = "Dizigecesi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/diziler" to "Popüler Diziler",
        "$mainUrl/filmler" to "Yeni Filmler",
        "$mainUrl/trend-diziler" to "Trend Diziler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val target = if (page <= 1) request.data else "${request.data}?page=$page"
        val items = parseCards(app.get(target).document)
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val href = fixUrlNull(element.attr("href")) ?: return null
        if (!href.contains("/dizi/") && !href.contains("/film/")) return null

        val img = element.selectFirst("img")
        val title = img?.attr("alt")?.trim()?.ifBlank { null }
            ?: element.text().trim().ifBlank { null }
            ?: return null
        val poster = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src")?.ifBlank { null }
        )

        return if (href.contains("/film/")) {
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
        }
    }

    private fun parseCards(document: Document): List<SearchResponse> =
        document.select("a:has(img)")
            .mapNotNull { toSearchResult(it) }
            .distinctBy { it.url.trimEnd('/') }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val term = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val items = parseCards(app.get("$mainUrl/arama/$term").document)
        return newSearchResponseList(items, hasNext = false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.title().substringBefore("izle").substringBefore("-").trim()
        val poster = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst("div.series-info-detail__image img, img.cover")
                    ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        )
        val description = document.selectFirst("meta[property='og:description']")?.attr("content")
            ?: document.selectFirst("div.series-info-detail__content, div.description")?.text()?.trim()

        if (url.contains("/film/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                plot = description
            }
        }

        val seasonRegex = Regex("""(\d+)-sezon/(\d+)-bolum""")
        val episodes = document.select("a[href*='-sezon/']")
            .filter { it.attr("href").contains("-bolum") }
            .mapNotNull { a ->
                val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
                val match = seasonRegex.find(href)
                val season = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                val episode = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1
                newEpisode(href) {
                    this.name = "$season. Sezon $episode. Bölüm"
                    this.season = season
                    this.episode = episode
                }
            }
            .distinctBy { it.data }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
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
        // Her sunucu dugmesi bir sayisal embed kimligi tasiyor; ayni kimlik
        // birden fazla dugmede tekrar ediyor.
        val embedIds = document.select("[data-embed]")
            .map { it.attr("data-embed").trim() }
            .filter { it.isNotBlank() && it.all(Char::isDigit) }
            .distinct()

        var found = false
        embedIds.amap { id ->
            runCatching {
                val response = app.post(
                    "$mainUrl/ajax/embed",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to data),
                    data = mapOf("id" to id),
                ).text
                val bridgeSrc = Regex("""<iframe[^>]+src=["']([^"']+)["']""")
                    .find(response)?.groupValues?.get(1) ?: return@runCatching
                // Ara sayfa: asil oynaticiyi (VidMoly, short.icu, upns...) iframe'liyor.
                val bridgeUrl = fixUrl(bridgeSrc)
                val bridge = app.get(bridgeUrl, headers = mapOf("Referer" to "$mainUrl/")).document
                val playerSrc = bridge.selectFirst("iframe#main-iframe, iframe")?.attr("src")
                    ?: Regex("""src=["']([^"']+)["']""").find(bridge.html())?.groupValues?.get(1)
                    ?: return@runCatching
                loadExtractor(fixUrl(playerSrc), bridgeUrl, subtitleCallback) { link ->
                    found = true
                    callback(link)
                }
            }
        }
        return found
    }
}
