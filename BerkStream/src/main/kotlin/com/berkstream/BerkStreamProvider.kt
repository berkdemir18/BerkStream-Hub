package com.berkstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Document
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

class BerkStreamProvider : TmdbProvider() {
    override var name = "BerkStream"
    override val apiName = "BerkStream"
    override var lang = "tr"
    override val useMetaLoadResponse = true
    override val usesWebView = true
    override val hasQuickSearch = true
    override val quickSearchTimeoutMs = 12_000L
    override val getMainPageTimeoutMs = 45_000L
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = listOf(
        MainPageData("🎬  VİZYON • BU HAFTA", "cinema", false),
        MainPageData("🔥  BUGÜN POPÜLER", "trending", false),
        MainPageData("🍿  FİLMLER • EN ÇOK İZLENEN", "movies", false),
        MainPageData("📺  DİZİLER • GÜNDEMDE", "series", false),
        MainPageData("ᴺ  NETFLIX • FİLM + DİZİ", "platform:8", false),
        MainPageData("▶  PRIME VIDEO • FİLM + DİZİ", "platform:119", false),
        MainPageData("✨  DISNEY+ • FİLM + DİZİ", "platform:337", false),
        MainPageData("🟣  HBO MAX • FİLM + DİZİ", "platform:1899", false),
        MainPageData("🔴  TABİİ • FİLM + DİZİ", "platform:2235", false),
        MainPageData("📡  CANLI TV", "live", true),
        MainPageData("🎲  BUGÜN NE İZLESEM?", "surprise", false),
    )

    private val boxOfficeUrl = "https://boxofficeturkiye.com/seanslar"
    private val tmdbWebUrl = "https://www.themoviedb.org"
    private val requestHeaders = mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.7")
    private val aliases = ConcurrentHashMap<Int, Set<String>>()
    private val shelfCache = ConcurrentHashMap<String, CachedShelf>()

    /**
     * Adlar gomulu saglayicilarin gercek `name` degerleriyle birebir ayni olmali;
     * eslesmeyen ad listenin sonuna dusuyor ve iyi kaynak taramanin disinda kaliyor.
     */
    private val providerPriority = listOf(
        "BerkStream Kaynakları", "plt-stream",
        // Dizi
        "Dizilla", "DiziPal", "DiziYou", "DiziBox", "SezonlukDizi", "DiziGom", "DiziMag",
        "RoketDizi", "YabanciDizi", "TvDiziler", "DiziMom", "DDizi", "DiziKorea",
        "powerDizi", "DiziPalOriginal", "WebdramaTurkey2", "KoreanTurk",
        // Film
        "HDFilmCehennemi", "HDFilmCehennemi2", "HDFilmDelisi", "HDFilmİzle", "HDFilmSitesi",
        "FilmMakinesi", "FullHDFilm", "FullHDFilmizlesene", "FullHDFilmİzlede",
        "SuperFilmGeldi", "JetFilmizle", "SinemaCX", "SetFilmIzle", "WebteIzle",
        "FilmModu", "FilmKovası", "SelcukFlix", "UgurFilm", "XPrime", "Sinewix",
        "4KFilmİzlesene", "WFilmİzle", "Filmİzleİlk", "Watch2Movies", "KultFilmler",
        "RareFilmm", "RecTV", "powerSinema",
    )

    /** Tek bir saglayicinin tarama suresi. */
    private val providerScanTimeoutMs = 9_000L

    /** Butun saglayici taramasinin toplam butcesi. */
    private val providerScanBudgetMs = 26_000L

    private val liveProviderPriority = listOf("plt-tv", "CanliTV", "InatBox", "RecTV", "vavooSpor")

    private val seriesTypes = setOf(
        TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.AsianDrama, TvType.OVA,
    )

    private data class CachedShelf(val savedAt: Long, val items: List<SearchResponse>)

    @Serializable
    data class CrossMetaData(
        @JsonProperty("isSuccess") @SerialName("isSuccess") val isSuccess: Boolean,
        @JsonProperty("movies") @SerialName("movies") val movies: List<Pair<String, String>> = emptyList(),
    )

    private fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('ı', 'i')
        .replace(Regex("[^A-Za-z0-9]"), "")
        .lowercase()

    private suspend fun cachedShelf(
        key: String,
        lifetimeMs: Long = 15 * 60 * 1000L,
        loader: suspend () -> List<SearchResponse>,
    ): List<SearchResponse> {
        val now = System.currentTimeMillis()
        shelfCache[key]?.takeIf { now - it.savedAt < lifetimeMs }?.let { return it.items }
        return try {
            loader().also { loaded ->
                if (loaded.isNotEmpty()) shelfCache[key] = CachedShelf(now, loaded)
            }
        } catch (error: Exception) {
            logError(error)
            shelfCache[key]?.items.orEmpty()
        }
    }

    /**
     * TMDB'nin `/discover/<tur>` rotasi artik `/<tur>`e 301 ile yonleniyor ve
     * yonlendirmede **butun query parametreleri dusuyor**. Eski adres
     * kullanildiginda watch_region ve with_watch_providers kayboluyor, bu yuzden
     * Netflix / Prime / Disney+ / HBO / tabii raflarinin hepsi ayni "populer
     * filmler" listesini gosteriyordu. Dogru rota parametresiz olan.
     */
    private fun tmdbDiscoverUrl(mediaType: String, providerId: String? = null): String {
        val providerQuery = providerId?.let {
            "&with_watch_providers=$it&with_watch_monetization_types=flatrate"
        }.orEmpty()
        return "$tmdbWebUrl/$mediaType?watch_region=TR&sort_by=popularity.desc$providerQuery"
    }

    private fun Document.toTmdbCards(limit: Int = 24): List<SearchResponse> =
        select("#media-list div[data-object-id]")
            .mapNotNull { card ->
                val titleLink = card.selectFirst("a[data-media-type][href]:has(h2)")
                    ?: return@mapNotNull null
                if (titleLink.attr("data-media-adult").equals("true", ignoreCase = true)) {
                    return@mapNotNull null
                }

                val title = titleLink.selectFirst("h2")?.text()?.trim().orEmpty()
                val href = titleLink.attr("href")
                val mediaType = titleLink.attr("data-media-type")
                if (title.isBlank() || href.isBlank() || mediaType !in setOf("movie", "tv")) {
                    return@mapNotNull null
                }

                val id = Regex("/(?:movie|tv)/(\\d+)").find(href)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                val poster = card.selectFirst("img.poster")?.attr("src")?.takeIf(String::isNotBlank)
                val year = Regex("\\b(19|20)\\d{2}\\b")
                    .find(card.selectFirst(".release_date")?.text().orEmpty())
                    ?.value?.toIntOrNull()
                id?.let { aliases[it] = setOf(title) }

                if (mediaType == "tv") {
                    newTvSeriesSearchResponse(title, "$tmdbWebUrl$href", TvType.TvSeries, false) {
                        this.id = id
                        this.posterUrl = poster
                        this.year = year
                    }
                } else {
                    newMovieSearchResponse(title, "$tmdbWebUrl$href", TvType.Movie, false) {
                        this.id = id
                        this.posterUrl = poster
                        this.year = year
                    }
                }
            }
            .distinctBy { it.url }
            .take(limit)

    private suspend fun tmdbShelf(mediaType: String, providerId: String? = null): List<SearchResponse> {
        val cacheKey = "tmdb:$mediaType:${providerId.orEmpty()}"
        return cachedShelf(cacheKey) {
            app.get(tmdbDiscoverUrl(mediaType, providerId), headers = requestHeaders)
                .document.toTmdbCards()
        }
    }

    private fun interleave(
        first: List<SearchResponse>, second: List<SearchResponse>, limit: Int = 24,
    ): List<SearchResponse> {
        val combined = ArrayList<SearchResponse>(limit)
        for (index in 0 until maxOf(first.size, second.size)) {
            first.getOrNull(index)?.let(combined::add)
            second.getOrNull(index)?.let(combined::add)
            if (combined.size >= limit) break
        }
        return combined.distinctBy { it.url }.take(limit)
    }

    private suspend fun mixedTmdbShelf(providerId: String? = null): List<SearchResponse> =
        interleave(tmdbShelf("movie", providerId), tmdbShelf("tv", providerId))

    private suspend fun cinemaShelf(): List<SearchResponse> = cachedShelf("cinema", 30 * 60 * 1000L) {
        app.get(boxOfficeUrl, headers = requestHeaders).document
            .select(".c-section__body")
            .filter {
                val heading = normalize(it.selectFirst("h3")?.text().orEmpty())
                heading.contains("vizyona") || heading.contains("vizyondaki")
            }
            .flatMap { it.select(".c-sessions__grid > a[href^=/film/]") }
            .distinctBy { normalize(it.selectFirst("h4")?.text().orEmpty()) }
            .take(24)
            .amap { card ->
                val title = card.selectFirst("h4")?.text()?.trim().orEmpty()
                if (title.isBlank()) return@amap null
                val results = super.search(title, 1)
                    ?.items?.filterIsInstance<MovieSearchResponse>().orEmpty()
                val exact = results.firstOrNull { normalize(it.name) == normalize(title) }
                    ?: results.firstOrNull()
                exact?.apply {
                    id?.let { movieId -> aliases[movieId] = setOf(title, name) }
                    card.selectFirst("img")?.attr("src")?.takeIf(String::isNotBlank)
                        ?.let { posterUrl = it }
                }
            }
            .filterNotNull()
    }

    private suspend fun liveTvShelf(): List<SearchResponse> = cachedShelf("live", 5 * 60 * 1000L) {
        apis.filter { api ->
            api.name != name && api.providerType != ProviderType.MetaProvider &&
                (TvType.Live in api.supportedTypes ||
                    liveProviderPriority.any { it.equals(api.name, ignoreCase = true) })
        }.sortedBy { api ->
            liveProviderPriority.indexOfFirst { it.equals(api.name, ignoreCase = true) }
                .let { if (it == -1) Int.MAX_VALUE else it }
        }.take(5).amap { api ->
            try {
                val shelf = api.mainPage.firstOrNull() ?: return@amap emptyList()
                api.getMainPage(1, MainPageRequest(shelf.name, shelf.data, shelf.horizontalImages))
                    ?.items?.flatMap { it.list }.orEmpty()
            } catch (error: Throwable) {
                logError(Exception(error))
                emptyList()
            }
        }.flatten().distinctBy { normalize(it.name) }.take(36)
    }

    private suspend fun surpriseShelf(): List<SearchResponse> {
        val daySeed = System.currentTimeMillis() / 86_400_000L
        return mixedTmdbShelf().sortedBy { it.url.hashCode().toLong() xor daySeed }.take(12)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest) =
        if (page > 1) {
            newHomePageResponse(request, emptyList<SearchResponse>(), false)
        } else {
            val items = when {
                request.data == "cinema" -> cinemaShelf().ifEmpty { tmdbShelf("movie") }
                request.data == "trending" -> mixedTmdbShelf()
                request.data == "movies" -> tmdbShelf("movie")
                request.data == "series" -> tmdbShelf("tv")
                request.data == "live" -> liveTvShelf()
                request.data == "surprise" -> surpriseShelf()
                request.data.startsWith("platform:") ->
                    mixedTmdbShelf(request.data.substringAfter("platform:"))
                else -> emptyList()
            }
            newHomePageResponse(request, items, false)
        }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        if (query.length < 2) return emptyList()
        return super.search(query, 1)?.items?.take(12)
    }

    /**
     * Arama artik TMDB uzerinden degil, dogrudan gomulu kaynaklarda yapiliyor.
     * Sonuclar ilgili saglayicinin kendi kaydi oldugu icin tiklandiginda
     * TMDB eslestirmesine hic ugramadan o kaynak aciliyor.
     */
    override suspend fun search(query: String, page: Int): SearchResponseList? {
        if (page > 1) return newSearchResponseList(emptyList(), false)
        val direct = scanProviders(validApisFor(supportedTypes + seriesTypes)) { api ->
            api.searchSafely(query).take(4)
        }.flatten().distinctBy { "${it.apiName}|${normalize(it.name)}" }
        if (direct.isNotEmpty()) return newSearchResponseList(direct, false)
        // Hicbir kaynak cevap vermezse en azindan TMDB sonucu donsun.
        return super.search(query, page)
    }

    private fun validApisFor(types: Set<TvType>) = apis.filter {
        it.name != name && it.lang == "tr" && it.providerType != ProviderType.MetaProvider &&
            it.supportedTypes.any(types::contains)
    }.sortedBy { api ->
        providerPriority.indexOfFirst { it.equals(api.name, ignoreCase = true) }
            .let { if (it == -1) Int.MAX_VALUE else it }
    }.take(18)

    /**
     * Saglayicilari paralel tarar ama hem tek tek hem de toplamda sureyi
     * sinirlar. Zaman asimi olmadan olu bir site tum icerik sayfasini sonsuza
     * kadar "yukleniyor"da tutuyordu; pakete 60+ kaynak girince bu kacinilmaz
     * hale geldi.
     */
    private suspend fun <T : Any> scanProviders(
        providers: List<MainAPI>,
        block: suspend (MainAPI) -> T?,
    ): List<T> = withTimeoutOrNull(providerScanBudgetMs) {
        providers.amap { api ->
            try {
                withTimeoutOrNull(providerScanTimeoutMs) { block(api) }
            } catch (error: Throwable) {
                // Exception DEGIL Throwable: eski API'yi uygulamayan saglayicilar
                // NotImplementedError firlatiyor ve o bir Error. Exception yakalanınca
                // hata yukari kaciyor, CloudStream de tum icerik sayfasini
                // "An operation is not implemented" diye dusuruyordu.
                logError(Exception("${api.name}: ${error.message}", error))
                null
            }
        }
    }.orEmpty().filterNotNull()

    /**
     * Saglayicilarin bir kismi eski `search(query)`, bir kismi yeni
     * `search(query, page)` imzasini uyguluyor. Uygulanmayan taraf
     * NotImplementedError firlattigi icin ikisi de denenir.
     */
    private suspend fun MainAPI.searchSafely(query: String): List<SearchResponse> {
        try {
            return search(query).orEmpty()
        } catch (_: NotImplementedError) {
        }
        return try {
            search(query, 1)?.items.orEmpty()
        } catch (_: NotImplementedError) {
            emptyList()
        }
    }

    private fun aliasesFor(url: String, fallback: String): List<String> {
        val tmdbId = Regex("themoviedb\\.org/(?:movie|tv)/(\\d+)")
            .find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return buildSet {
            add(fallback)
            tmdbId?.let { aliases[it] }?.let(::addAll)
        }.filter(String::isNotBlank)
    }

    private suspend fun loadSeriesFromProviders(base: TvSeriesLoadResponse, url: String): LoadResponse {
        val queryNames = aliasesFor(url, base.name)
        val normalizedNames = queryNames.map(::normalize).toSet()
        val matches = scanProviders(validApisFor(seriesTypes)) { api ->
            val hit = queryNames.firstNotNullOfOrNull { query ->
                api.searchSafely(query).firstOrNull { result ->
                    normalize(result.name) in normalizedNames && result.type in seriesTypes &&
                        (result !is TvSeriesSearchResponse || result.year == null ||
                            base.year == null || result.year == base.year)
                }
            } ?: return@scanProviders null
            api.load(hit.url)?.takeIf { it !is MovieLoadResponse }
        }
        return matches.firstOrNull() ?: base
    }

    override suspend fun load(url: String): LoadResponse? {
        val base = super.load(url) ?: return null
        if (base is TvSeriesLoadResponse) return loadSeriesFromProviders(base, url)
        if (base !is MovieLoadResponse) {
            throw ErrorLoadingException("BerkStream bu içerik türünü henüz desteklemiyor")
        }

        val queryNames = aliasesFor(url, base.name)
        val normalizedNames = queryNames.map(::normalize).toSet()
        val matches = scanProviders(validApisFor(setOf(TvType.Movie, TvType.AnimeMovie))) { api ->
            val hit = queryNames.firstNotNullOfOrNull { query ->
                api.searchSafely(query).firstOrNull { result ->
                    normalize(result.name) in normalizedNames &&
                        (result !is MovieSearchResponse || result.year == null ||
                            base.year == null || result.year == base.year)
                }
            } ?: return@scanProviders null
            (api.load(hit.url) as? MovieLoadResponse)?.let { it.apiName to it.dataUrl }
        }

        base.dataUrl = CrossMetaData(matches.isNotEmpty(), matches).toJson()
        return base
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val metadata = tryParseJson<CrossMetaData>(data) ?: return false
        if (!metadata.isSuccess) return false
        metadata.movies.amap { (providerName, providerData) ->
            try {
                getApiFromNameNull(providerName)?.loadLinks(
                    providerData, isCasting, subtitleCallback, callback,
                )
            } catch (error: Throwable) {
                logError(Exception(error))
            }
        }
        return true
    }
}
