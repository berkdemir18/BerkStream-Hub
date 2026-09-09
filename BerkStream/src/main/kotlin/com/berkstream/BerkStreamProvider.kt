package com.berkstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.Score
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
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.withTimeoutOrNull
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicInteger

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
    override val getMainPageTimeoutMs = 20_000L
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
    override val mainPage: List<MainPageData>
        get() = allShelves.filter { data ->
            when {
                data.data == "personal" -> BerkStreamSettings.personalShelfEnabled
                data.data == "fresh" -> BerkStreamSettings.freshShelfEnabled
                data.data in setOf("dice", "onthisday") -> BerkStreamSettings.discoveryShelvesEnabled
                data.data.contains("with_watch_providers") -> BerkStreamSettings.platformShelvesEnabled
                data.data.contains("with_genres") -> BerkStreamSettings.genreShelvesEnabled
                else -> true
            }
        }

    private val allShelves = listOf(
        shelf("🎬  VİZYONDAKİ FİLMLER", "movie", "movie/now_playing?region=TR"),
        shelf("🔥  GÜNÜN TRENDLERİ", "mixed", "trending/all/day"),
        MainPageData("🎯  SANA ÖZEL", "personal", false),
        shelf("📈  HAFTANIN POPÜLER FİLMLERİ", "movie", "trending/movie/week"),
        shelf("📺  HAFTANIN POPÜLER DİZİLERİ", "tv", "trending/tv/week"),
        shelf("🆕  YENİ ÇIKAN FİLMLER", "movie", "discover/movie?sort_by=primary_release_date.desc&vote_count.gte=25"),
        shelf("⭐  EN YÜKSEK PUANLI FİLMLER", "movie", "discover/movie?sort_by=vote_average.desc&vote_count.gte=400"),
        shelf("⭐  EN YÜKSEK PUANLI DİZİLER", "tv", "discover/tv?sort_by=vote_average.desc&vote_count.gte=250"),
        shelf("🇹🇷  TÜRK DİZİLERİ", "tv", "discover/tv?with_original_language=tr&sort_by=popularity.desc"),
        shelf("🇹🇷  TÜRK FİLMLERİ", "movie", "discover/movie?with_original_language=tr&sort_by=popularity.desc"),
        MainPageData("🆕  KAYNAKLARDA YENİ", "fresh", false),
        shelf("ᴺ  NETFLIX • FİLM", "movie", "discover/movie?with_watch_providers=8"),
        shelf("ᴺ  NETFLIX • DİZİ", "tv", "discover/tv?with_watch_providers=8"),
        shelf("▶  PRIME VIDEO • FİLM", "movie", "discover/movie?with_watch_providers=119"),
        shelf("▶  PRIME VIDEO • DİZİ", "tv", "discover/tv?with_watch_providers=119"),
        shelf("✨  DISNEY+ • FİLM", "movie", "discover/movie?with_watch_providers=337"),
        shelf("✨  DISNEY+ • DİZİ", "tv", "discover/tv?with_watch_providers=337"),
        shelf("🟣  HBO MAX • FİLM", "movie", "discover/movie?with_watch_providers=1899"),
        shelf("🟣  HBO MAX • DİZİ", "tv", "discover/tv?with_watch_providers=1899"),
        shelf("  APPLE TV+ • FİLM", "movie", "discover/movie?with_watch_providers=350&watch_region=US"),
        shelf("  APPLE TV+ • DİZİ", "tv", "discover/tv?with_watch_providers=350&watch_region=US"),
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
        shelf("📅  BU HAFTA YENİ BÖLÜM", "tv", "tv/on_the_air"),
        shelf("💎  GİZLİ CEVHERLER", "movie", "discover/movie?sort_by=vote_average.desc&vote_count.gte=200&vote_count.lte=1200"),
        shelf("⏱️  KISA GECE • 95 DK ALTI", "movie", "discover/movie?with_runtime.lte=95&vote_count.gte=300&sort_by=vote_average.desc"),
        shelf("🕰️  90'LAR KLASİKLERİ", "movie", "discover/movie?primary_release_date.gte=1990-01-01&primary_release_date.lte=1999-12-31&sort_by=vote_average.desc&vote_count.gte=500"),
        shelf("🏆  OSCAR YOLUNDA", "movie", "discover/movie?sort_by=vote_average.desc&vote_count.gte=1500&primary_release_date.gte=2015-01-01"),
        MainPageData("🎲  ZAR AT", "dice", false),
        MainPageData("🗓️  YILLAR ÖNCE BUGÜN", "onthisday", false),
        MainPageData("⚽  CANLI SPOR", "sports", true),
        MainPageData("📡  CANLI TV", "live", true),
    )

    private val domainsUrl =
        "https://raw.githubusercontent.com/berkdemir18/BerkStream-Hub/builds/domains.json"

    private val subtitleApiUrl = "https://opensubtitles-v3.strem.io/subtitles"

    private val wantedSubtitleLanguages = setOf("tur", "tr", "eng", "en")

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

    private class CachedShelf(val savedAt: Long, val items: List<SearchResponse>)

    private val shelfCache = java.util.concurrent.ConcurrentHashMap<String, CachedShelf>()

    /**
     * "Sana Ozel" ve "Kaynaklarda Yeni" raflari onlarca ag istegi yapiyor ve
     * ana sayfanin en ustunde duruyorlar; onbelleksiz her acilista uygulamayi
     * bekletiyorlardi.
     */
    private fun cachedShelf(key: String): List<SearchResponse>? =
        shelfCache[key]?.takeIf { System.currentTimeMillis() - it.savedAt < shelfCacheMs }?.items

    private fun putShelf(key: String, items: List<SearchResponse>) {
        if (items.isNotEmpty()) shelfCache[key] = CachedShelf(System.currentTimeMillis(), items)
    }

    private val shelfCacheMs = 10 * 60 * 1000L

    /** Tek bir saglayicinin tarama suresi; ayar ekranindan degistirilebiliyor. */
    private val providerScanTimeoutMs: Long
        get() = BerkStreamSettings.scanTimeoutSeconds * 1000L

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
        @JsonProperty("vote_average") val voteAverage: Double? = null,
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

    /**
     * Kaynak sitelerin baslıklari "X izle", "X Türkçe Dublaj 1080p" gibi ekler
     * tasiyor. Tam esleşme aramasi bu yuzden cok icerigi kaciriyordu; once bu
     * ekler temizleniyor, sonra bulanik karsilastirma yapiliyor.
     */
    private fun looseTitle(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('ı', 'i')
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(
            Regex(
                "\\b(izle|seyret|full|hd|fullhd|4k|1080p|720p|480p|turkce|turkiye|dublaj|" +
                    "altyazili|altyazi|filmi|film|dizisi|dizi|online|tek|parca|part|sezon|bolum|" +
                    "yerli|yabanci|hdfilm|tr)\\b",
            ),
            " ",
        )
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun titleMatches(candidate: String, target: String): Boolean {
        val a = looseTitle(candidate)
        val b = looseTitle(target)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        if (normalize(candidate) == normalize(target)) return true
        return FuzzySearch.tokenSetRatio(a, b) >= 88
    }

    private data class DomainConfig(
        @JsonProperty("overrides") val overrides: Map<String, String> = emptyMap(),
        @JsonProperty("disabled") val disabled: List<String> = emptyList(),
    )

    private data class StremioSubtitle(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("lang") val lang: String? = null,
    )

    private data class StremioSubtitleList(
        @JsonProperty("subtitles") val subtitles: List<StremioSubtitle> = emptyList(),
    )

    @Volatile
    private var domainsApplied = false
    private val disabledProviders = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val failureCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * CI taramasi GitHub'in sunucularindan yapiliyor; Turkiye'den erisilemeyen
     * bir site oradan ayakta gorunebiliyor, yani yayimlanan liste kullanicinin
     * agini birebir yansitmiyor. Bu yuzden cihaz tarafinda da olcuyoruz: ust uste
     * cevap veremeyen kaynak bu oturumda taramaya sokulmuyor.
     */
    private fun noteFailure(api: MainAPI) {
        val key = normalize(api.name)
        val total = failureCounts.merge(key, 1, Int::plus) ?: 1
        if (total >= 2) disabledProviders.add(key)
    }

    /**
     * Kaynak siteleri surekli adres degistirdigi icin adresler pakete gomulu
     * kalamiyor. CI haftalik tarama yapip `domains.json` uretiyor; burada o liste
     * uygulaniyor: guncel adres saglayicinin mainUrl'ine yaziliyor, cevap
     * vermeyen kaynaklar da taramaya hic sokulmuyor.
     */
    init {
        BerkStreamSettings.cacheCleaner = { shelfCache.clear() }
        BerkStreamSettings.domainRefresher = {
            domainsApplied = false
            disabledProviders.clear()
            failureCounts.clear()
        }
    }

    private suspend fun ensureDomains() {
        if (domainsApplied) return
        domainsApplied = true
        runCatching {
            val config = tryParseJson<DomainConfig>(app.get(domainsUrl).text) ?: return
            config.disabled.forEach { disabledProviders.add(normalize(it)) }
            if (config.overrides.isEmpty()) return
            val byName = apis.associateBy { normalize(it.name) }
            config.overrides.forEach { (providerName, url) ->
                byName[normalize(providerName)]?.mainUrl = url
            }
        }.onFailure { logError(Exception("Adres listesi uygulanamadi", it)) }
    }

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
                this.score = Score.from10(voteAverage?.takeIf { it > 0.0 })
            }
        } else {
            newMovieSearchResponse(label, url, TvType.Movie, false) {
                this.id = itemId
                this.posterUrl = poster
                this.year = releaseYear
                this.score = Score.from10(voteAverage?.takeIf { it > 0.0 })
            }
        }
    }

    private suspend fun tmdbShelf(path: String, mediaType: String, page: Int): Pair<List<SearchResponse>, Boolean> {
        val separator = if (path.contains("?")) "&" else "?"
        // Raf kendi bolgesini belirtmisse ona dokunma: Apple TV+ katalogu TR'de
        // bos donuyor, o raf US bolgesiyle cekiliyor.
        val region = if (path.contains("watch_region=")) "" else "&watch_region=TR"
        val url = "$tmdbApiUrl/$path$separator" +
            "api_key=$tmdbApiKey&language=tr-TR&include_adult=false$region&page=$page"
        val parsed = tryParseJson<TmdbPage>(app.get(url).text) ?: return emptyList<SearchResponse>() to false
        val items = parsed.results.mapNotNull { it.toSearchResponse(mediaType) }
        val hasNext = page < (parsed.totalPages ?: 1)
        return items to hasNext
    }

    private val sportsWords = listOf(
        "spor", "sport", "bein", "tivibu", "smart", "futbol", "lig", "mac", "match",
        "eurosport", "trt spor", "aspor", "tabii spor", "exxen",
    )

    private fun isSporty(label: String): Boolean {
        val text = looseTitle(label)
        return sportsWords.any { text.contains(it) }
    }

    /**
     * Canli yayin kaynaklari birden fazla kategori rafi tasiyor: InatBox'ta 25,
     * RecTV'de 14 raf var. Onceden yalnizca ilk raf okundugu icin toplam 10-15
     * kanal gorunuyordu; artik kategorilerin tamami geziliyor ve spor rafi da
     * kategori adindan suzuluyor.
     */
    private suspend fun liveShelf(onlySports: Boolean, page: Int): List<SearchResponse> {
        ensureDomains()
        val providers = apis
            .filter { api ->
                api.name != name && api.providerType != ProviderType.MetaProvider &&
                    normalize(api.name) !in disabledProviders &&
                    (TvType.Live in api.supportedTypes ||
                        liveProviderPriority.any { it.equals(api.name, ignoreCase = true) })
            }
            .sortedBy { api ->
                liveProviderPriority.indexOfFirst { it.equals(api.name, ignoreCase = true) }
                    .let { if (it == -1) Int.MAX_VALUE else it }
            }
            .take(6)

        return scanProviders(providers) { api ->
            val shelves = api.mainPage
                .filter { !onlySports || isSporty(it.name) }
                .take(if (onlySports) 6 else 12)
            shelves.mapNotNull { shelf ->
                runCatching {
                    api.getMainPage(
                        page,
                        MainPageRequest(shelf.name, shelf.data, shelf.horizontalImages),
                    )?.items?.flatMap { it.list }
                }.getOrNull()
            }.flatten()
        }
            .flatten()
            .let { items ->
                // Kategori adi spor degilse bile kanal adi spor olabilir.
                if (!onlySports) items else {
                    val byShelf = items.filter { isSporty(it.name) }
                    if (byShelf.size >= 8) byShelf else items
                }
            }
            .distinctBy { "${it.apiName}|${normalize(it.name)}" }
            .take(240)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        when (request.data) {
            "live", "sports" -> {
                val sports = request.data == "sports"
                val key = if (sports) "sports" else "live"
                if (page == 1) cachedShelf(key)?.let { return newHomePageResponse(request, it, true) }
                val items = liveShelf(sports, page)
                if (page == 1) putShelf(key, items)
                return newHomePageResponse(request, items, items.isNotEmpty())
            }
            "personal" -> {
                val (items, hasNext) = try {
                    if (page == 1) {
                        cachedShelf("personal")?.let { return newHomePageResponse(request, it, true) }
                    }
                    personalShelf(page).also { if (page == 1) putShelf("personal", it.first) }
                } catch (error: Throwable) {
                    logError(Exception(error))
                    emptyList<SearchResponse>() to false
                }
                return newHomePageResponse(request, items, hasNext)
            }
            "fresh" -> {
                if (page > 1 || !BerkStreamSettings.freshShelfEnabled) {
                    return newHomePageResponse(request, emptyList<SearchResponse>(), false)
                }
                cachedShelf("fresh")?.let { return newHomePageResponse(request, it, false) }
                val items = freshFromProviders()
                putShelf("fresh", items)
                return newHomePageResponse(request, items, false)
            }
            "dice" -> {
                val randomPage = (1..25).random()
                val (items, _) = tmdbShelf(
                    "discover/movie?sort_by=popularity.desc&vote_count.gte=120",
                    "movie",
                    randomPage,
                )
                return newHomePageResponse(request, items.shuffled().take(20), true)
            }
            "onthisday" -> {
                val calendar = java.util.Calendar.getInstance()
                val month = calendar.get(java.util.Calendar.MONTH) + 1
                val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                val year = calendar.get(java.util.Calendar.YEAR) - (5 + (page - 1) * 5)
                val date = String.format("%04d-%02d-%02d", year, month, day)
                val (items, _) = tmdbShelf(
                    "discover/movie?primary_release_date.gte=$date&primary_release_date.lte=$date",
                    "movie",
                    1,
                )
                return newHomePageResponse(request, items, page < 6)
            }
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
        ensureDomains()
        val direct = if (page > 1) emptyList() else scanProviders(
            validApisFor(movieTypes + seriesTypes),
        ) { api -> api.searchSafely(query).take(3) }.flatten()
        // Berk'in istegi: kaynak sonuclari once. Onlar dogrudan oynatilabiliyor,
        // TMDB kaydi ise once eslestirme gerektiriyor.
        val combined = (direct + meta).distinctBy { "${it.apiName}|${normalize(it.name)}" }
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
        ensureDomains()
        val season = link.season
        val episode = link.episode
        val isSeries = season != null || episode != null
        rememberWatched(title)

        val linkCount = AtomicInteger(0)
        val countingCallback: (ExtractorLink) -> Unit = { extractor ->
            linkCount.incrementAndGet()
            callback(extractor)
        }

        if (BerkStreamSettings.subtitlesEnabled) loadStremioSubtitles(link, subtitleCallback)

        // Saglayicilar oncelik sirasina gore obekler halinde taraniyor: yeterli
        // link toplanınca kalan obekler hic denenmiyor. Hepsini birden beklemek
        // oynatmayi gereksiz geciktiriyordu.
        for (batch in validApisFor(if (isSeries) seriesTypes else movieTypes).chunked(6)) {
            scanProviders(batch) { api ->
                val hit = api.searchSafely(title)
                    .firstOrNull { titleMatches(it.name, title) }
                    ?: return@scanProviders null
                val response = api.load(hit.url) ?: return@scanProviders null
                val innerData = when {
                    isSeries && response is TvSeriesLoadResponse -> response.episodes.firstOrNull {
                        (season == null || it.season == season) &&
                            (episode == null || it.episode == episode)
                    }?.data
                    !isSeries && response is MovieLoadResponse -> response.dataUrl
                    else -> null
                } ?: return@scanProviders null
                api.loadLinks(innerData, isCasting, subtitleCallback, countingCallback)
                true
            }
            if (linkCount.get() >= BerkStreamSettings.linkTarget) break
        }
        return linkCount.get() > 0
    }

    /**
     * Altyazi: Stremio'nun kamuya acik OpenSubtitles kopruSu anahtarsiz calisiyor
     * ve TmdbLink zaten imdbID tasiyor. Kaynak sitenin kendi altyazisi olmadigi
     * durumlarda tek secenek bu.
     */
    private suspend fun loadStremioSubtitles(link: TmdbLink, subtitleCallback: (SubtitleFile) -> Unit) {
        val imdbId = link.imdbID?.takeIf { it.startsWith("tt") } ?: return
        val suffix = if (link.season != null && link.episode != null) {
            "series/$imdbId:${link.season}:${link.episode}"
        } else {
            "movie/$imdbId"
        }
        runCatching {
            val parsed = tryParseJson<StremioSubtitleList>(
                app.get("$subtitleApiUrl/$suffix.json").text,
            ) ?: return
            val allowed = when (BerkStreamSettings.subtitleLanguage) {
                1 -> setOf("tur", "tr")
                2 -> setOf("eng", "en")
                else -> wantedSubtitleLanguages
            }
            parsed.subtitles
                .filter { it.url != null && it.lang in allowed }
                .distinctBy { it.url }
                .take(14)
                .forEach { subtitle ->
                    val label = if (subtitle.lang?.startsWith("tu") == true) "Türkçe" else "İngilizce"
                    subtitleCallback(newSubtitleFile(label, subtitle.url!!))
                }
        }.onFailure { logError(Exception("Altyazi alinamadi", it)) }
    }

    private fun validApisFor(types: Set<TvType>) = apis.filter {
        it.name != name && it.lang == "tr" && it.providerType != ProviderType.MetaProvider &&
            it.supportedTypes.any(types::contains) &&
            normalize(it.name) !in disabledProviders
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
                withTimeoutOrNull(providerScanTimeoutMs) { block(api) } ?: run {
                    noteFailure(api)
                    null
                }
            } catch (error: Throwable) {
                noteFailure(api)
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
