package com.berkstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer

/**
 * BerkStream ana ekrani.
 *
 * Tasarim kararlari:
 * - Raflar TMDB'nin **API**'sinden besleniyor, web sayfasi kazinarak degil.
 *   Kazima yolunda her raf ~256 KB HTML indiriyordu ve TMDB rotayi degistirince
 *   sessizce bozuluyordu; API ~15 KB JSON donuyor, `language=tr-TR` ile
 *   basliklar Turkce geliyor ve `total_pages` sayesinde raflar sonsuz kayiyor.
 * - Icerik sayfasi acilirken saglayici taramasi YAPILMIYOR. Tarama artik
 *   yalnizca oynat'a basildiginda, [loadLinks] icinde calisiyor. Onceki surumde
 *   sayfayi acmak 18 saglayicinin cevabini beklemek demekti.
 */
class BerkStreamProvider : TmdbProvider() {
    override var name = "BerkStream"
    override val apiName = "BerkStream"
    override var lang = "tr"
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val quickSearchTimeoutMs = 12_000L
    override val getMainPageTimeoutMs = 45_000L
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdbApiUrl = "https://api.themoviedb.org/3"

    /** CloudStream'in kendi acik kaynakli TMDB anahtari; TmdbProvider da bunu kullaniyor. */
    private val tmdbApiKey = "e6333b32409e02a4a6eba6fb7ff866bb"

    private val imageUrl = "https://image.tmdb.org/t/p/w342"

    /**
     * Raf tanimi. `data` alani "<tur>|<tmdb yolu>" bicimindedir; tur karti
     * film mi dizi mi olarak kuracagimizi soyler, "mixed" ise TMDB'nin kendi
     * `media_type` alani kullanilir.
     */
    override val mainPage = listOf(
        shelf("🎬  VİZYONDAKİ FİLMLER", "movie", "movie/now_playing?region=TR"),
        shelf("🔥  GÜNÜN TRENDLERİ", "mixed", "trending/all/day"),
        shelf("📈  HAFTANIN POPÜLER FİLMLERİ", "movie", "trending/movie/week"),
        shelf("📺  HAFTANIN POPÜLER DİZİLERİ", "tv", "trending/tv/week"),
        shelf("🆕  YENİ ÇIKAN FİLMLER", "movie", "discover/movie?sort_by=primary_release_date.desc&vote_count.gte=25"),
        shelf("⭐  EN YÜKSEK PUANLI FİLMLER", "movie", "discover/movie?sort_by=vote_average.desc&vote_count.gte=400"),
        shelf("⭐  EN YÜKSEK PUANLI DİZİLER", "tv", "discover/tv?sort_by=vote_average.desc&vote_count.gte=250"),
        shelf("🇹🇷  TÜRK DİZİLERİ", "tv", "discover/tv?with_original_language=tr&sort_by=popularity.desc"),
        shelf("🇹🇷  TÜRK FİLMLERİ", "movie", "discover/movie?with_original_language=tr&sort_by=popularity.desc"),
        shelf("ᴺ  NETFLIX • FİLM", "movie", "discover/movie?with_watch_providers=8"),
        shelf("ᴺ  NETFLIX • DİZİ", "tv", "discover/tv?with_watch_providers=8"),
        shelf("▶  PRIME VIDEO • FİLM", "movie", "discover/movie?with_watch_providers=119"),
        shelf("▶  PRIME VIDEO • DİZİ", "tv", "discover/tv?with_watch_providers=119"),
        shelf("✨  DISNEY+ • FİLM", "movie", "discover/movie?with_watch_providers=337"),
        shelf("✨  DISNEY+ • DİZİ", "tv", "discover/tv?with_watch_providers=337"),
        shelf("🟣  HBO MAX • FİLM", "movie", "discover/movie?with_watch_providers=1899"),
        shelf("🟣  HBO MAX • DİZİ", "tv", "discover/tv?with_watch_providers=1899"),
        shelf("  APPLE TV+ • FİLM", "movie", "discover/movie?with_watch_providers=350"),
        shelf("🔴  TABİİ", "tv", "discover/tv?with_watch_providers=2235"),
        shelf("💥  AKSİYON", "movie", "discover/movie?with_genres=28&sort_by=popularity.desc"),
        shelf("😂  KOMEDİ", "movie", "discover/movie?with_genres=35&sort_by=popularity.desc"),
        shelf("👽  BİLİM KURGU", "movie", "discover/movie?with_genres=878&sort_by=popularity.desc"),
        shelf("😱  KORKU", "movie", "discover/movie?with_genres=27&sort_by=popularity.desc"),
        shelf("🎭  DRAM", "movie", "discover/movie?with_genres=18&sort_by=popularity.desc"),
        shelf("🕵️  GERİLİM", "movie", "discover/movie?with_genres=53&sort_by=popularity.desc"),
        shelf("💘  ROMANTİK", "movie", "discover/movie?with_genres=10749&sort_by=popularity.desc"),
        shelf("🎨  ANİMASYON • FİLM", "movie", "discover/movie?with_genres=16&sort_by=popularity.desc"),
        shelf("🧒  ANİMASYON • DİZİ", "tv", "discover/tv?with_genres=16&sort_by=popularity.desc"),
        shelf("🎌  ANİME", "tv", "discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc"),
        shelf("📖  BELGESEL", "movie", "discover/movie?with_genres=99&sort_by=popularity.desc"),
        MainPageData("📡  CANLI TV", "live", true),
    )

    private val liveProviderPriority = listOf("plt-tv", "CanliTV", "InatBox", "RecTV", "vavooSpor")

    private val movieTypes = setOf(TvType.Movie, TvType.AnimeMovie)

    private val seriesTypes = setOf(
        TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.AsianDrama, TvType.OVA,
    )

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

    private fun shelf(title: String, mediaType: String, path: String) =
        MainPageData(title, "$mediaType|$path", false)

    private data class TmdbItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("adult") val adult: Boolean? = null,
    )

    private data class TmdbPage(
        @JsonProperty("results") val results: List<TmdbItem> = emptyList(),
        @JsonProperty("total_pages") val totalPages: Int? = null,
    )

    private fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('ı', 'i')
        .replace(Regex("[^A-Za-z0-9]"), "")
        .lowercase()

    private fun TmdbItem.toSearchResponse(fallbackType: String): SearchResponse? {
        if (adult == true) return null
        val itemId = id ?: return null
        val kind = when (fallbackType) {
            "mixed" -> mediaType ?: return null
            else -> fallbackType
        }
        if (kind != "movie" && kind != "tv") return null
        val label = (if (kind == "tv") name ?: title else title ?: name)?.trim().orEmpty()
        if (label.isBlank()) return null
        val poster = posterPath?.takeIf(String::isNotBlank)?.let { "$imageUrl$it" }
        val releaseYear = (if (kind == "tv") firstAirDate else releaseDate)
            ?.take(4)?.toIntOrNull()
        // TmdbProvider.load() bu adres bicimini bekliyor: themoviedb.org/<tur>/<id>
        val url = "https://www.themoviedb.org/$kind/$itemId"
        return if (kind == "tv") {
            newTvSeriesSearchResponse(label, url, TvType.TvSeries, false) {
                this.id = itemId
                this.posterUrl = poster
                this.year = releaseYear
            }
        } else {
            newMovieSearchResponse(label, url, TvType.Movie, false) {
                this.id = itemId
                this.posterUrl = poster
                this.year = releaseYear
            }
        }
    }

    private suspend fun tmdbShelf(path: String, mediaType: String, page: Int): Pair<List<SearchResponse>, Boolean> {
        val separator = if (path.contains("?")) "&" else "?"
        val url = "$tmdbApiUrl/$path$separator" +
            "api_key=$tmdbApiKey&language=tr-TR&include_adult=false&watch_region=TR&page=$page"
        val parsed = tryParseJson<TmdbPage>(app.get(url).text) ?: return emptyList<SearchResponse>() to false
        val items = parsed.results.mapNotNull { it.toSearchResponse(mediaType) }
        val hasNext = page < (parsed.totalPages ?: 1)
        return items to hasNext
    }

    private suspend fun liveShelf(): List<SearchResponse> = apis
        .filter { api ->
            api.name != name && api.providerType != ProviderType.MetaProvider &&
                (TvType.Live in api.supportedTypes ||
                    liveProviderPriority.any { it.equals(api.name, ignoreCase = true) })
        }
        .sortedBy { api ->
            liveProviderPriority.indexOfFirst { it.equals(api.name, ignoreCase = true) }
                .let { if (it == -1) Int.MAX_VALUE else it }
        }
        .take(5)
        .amap { api ->
            try {
                val first = api.mainPage.firstOrNull() ?: return@amap emptyList()
                api.getMainPage(1, MainPageRequest(first.name, first.data, first.horizontalImages))
                    ?.items?.flatMap { it.list }.orEmpty()
            } catch (error: Throwable) {
                logError(Exception(error))
                emptyList()
            }
        }
        .flatten()
        .distinctBy { normalize(it.name) }
        .take(40)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data == "live") {
            val live = if (page > 1) emptyList() else liveShelf()
            return newHomePageResponse(request, live, false)
        }
        val mediaType = request.data.substringBefore('|', "movie")
        val path = request.data.substringAfter('|')
        val (items, hasNext) = try {
            tmdbShelf(path, mediaType, page)
        } catch (error: Throwable) {
            logError(Exception(error))
            emptyList<SearchResponse>() to false
        }
        return newHomePageResponse(request, items, hasNext)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        if (query.length < 2) return emptyList()
        return super.search(query, 1)?.items?.take(12)
    }

    /**
     * TMDB sonuclari once gelir (Turkce baslik, afis, dogru yil); arkasina
     * kaynaklarin kendi kayitlari eklenir, boylece TMDB'de olmayan bir icerik de
     * bulunabiliyor.
     */
    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val meta = try {
            super.search(query, page)?.items.orEmpty()
        } catch (error: Throwable) {
            logError(Exception(error))
            emptyList()
        }
        val direct = if (page > 1) emptyList() else scanProviders(
            validApisFor(movieTypes + seriesTypes),
        ) { api -> api.searchSafely(query).take(3) }.flatten()
        val combined = (meta + direct).distinctBy { "${it.apiName}|${normalize(it.name)}" }
        return newSearchResponseList(combined, meta.isNotEmpty())
    }

    /**
     * Kaynak taramasinin tamami burada. Icerik sayfasi acilirken degil, yalnizca
     * oynat'a basildiginda calisir.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val link = tryParseJson<TmdbLink>(data) ?: return false
        val title = link.movieName?.trim().orEmpty()
        if (title.isBlank()) return false
        val season = link.season
        val episode = link.episode
        val isSeries = season != null || episode != null
        val target = normalize(title)

        var found = false
        scanProviders(validApisFor(if (isSeries) seriesTypes else movieTypes)) { api ->
            val hit = api.searchSafely(title)
                .firstOrNull { normalize(it.name) == target }
                ?: return@scanProviders null
            val response = api.load(hit.url) ?: return@scanProviders null
            val innerData = when {
                isSeries && response is TvSeriesLoadResponse -> response.episodes.firstOrNull {
                    (season == null || it.season == season) && (episode == null || it.episode == episode)
                }?.data
                !isSeries && response is MovieLoadResponse -> response.dataUrl
                else -> null
            } ?: return@scanProviders null
            if (api.loadLinks(innerData, isCasting, subtitleCallback, callback)) found = true
            true
        }
        return found
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
     * sinirlar. Zaman asimi olmadan olu bir site tum akisi sonsuza kadar
     * bekletiyordu; pakete 60+ kaynak girince bu kacinilmaz hale geldi.
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
                // NotImplementedError firlatiyor ve o bir Error. Exception yakalaninca
                // hata yukari kaciyor, CloudStream de tum akisi
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
}
