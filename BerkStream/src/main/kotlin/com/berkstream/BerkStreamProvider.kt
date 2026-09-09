package com.berkstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.AnimeLoadResponse
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
        shelf("🏛️  TARİH • FİLM", "movie", "discover/movie?with_genres=36&sort_by=popularity.desc"),
        shelf("🎖️  SAVAŞ • FİLM", "movie", "discover/movie?with_genres=10752&sort_by=popularity.desc"),
        shelf("🤠  WESTERN • FİLM", "movie", "discover/movie?with_genres=37&sort_by=popularity.desc"),
        shelf("🎖️  SAVAŞ • DİZİ", "tv", "discover/tv?with_genres=10768&sort_by=popularity.desc"),
        shelf("🤠  WESTERN • DİZİ", "tv", "discover/tv?with_genres=37&sort_by=popularity.desc"),
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

    private val liveProviderPriority =
        listOf("plt-tv", "CanliTV", "InatBox", "RecTV", "vavooSpor", "BerkStream Canlı")

    private val movieTypes = setOf(TvType.Movie, TvType.AnimeMovie)

    /** BerkStream anime basliklarini da acabilmeli. */
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

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
        // Anime/cizgi: listede olmadiklari icin hepsi en sona dusuyor ve
        // take(18) onlari kesiyordu; One Piece gibi basliklarda hicbir anime
        // kaynagi taranmiyordu.
        "TurkAnime", "AnimeciX", "AsyaAnimeleri", "TrAnimeci", "AsyaWatch",
        "ÇizgiMax", "CizgiDuo", "CizgiPass",
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
    private val providerScanBudgetMs = 18_000L

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

        // Adayda hedefte gecmeyen anlamli bir kelime varsa bu baska bir yapimdir:
        // "dexter resurrection" ve "dexters laboratory", "dexter" degildir.
        val targetWords = b.split(" ").filter { it.isNotBlank() }.toSet()
        val extraWords = a.split(" ").filter { it.length > 2 && it !in targetWords }
        if (extraWords.isNotEmpty()) return false

        return FuzzySearch.tokenSetRatio(a, b) >= 92
    }

    private data class DomainConfig(
        @JsonProperty("overrides") val overrides: Map<String, String> = emptyMap(),
        @JsonProperty("disabled") val disabled: List<String> = emptyList(),
    )

    private data class TmdbExternalIds(
        @JsonProperty("imdb_id") val imdbId: String? = null,
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
    /**
     * Hangi kaynagin gercekten link urettigini ogreniyoruz. Elle yazilan
     * oncelik listesi bir tahmindi; basari sayaci zamanla gercek performansa
     * gore siralamayi duzeltiyor ve iyi kaynaklar one geciyor.
     */
    private fun noteSuccess(api: MainAPI) {
        val store = BerkStreamSettings.store ?: return
        val key = "hit_${normalize(api.name)}"
        runCatching {
            store.edit().putInt(key, (store.getInt(key, 0) + 1).coerceAtMost(500)).apply()
        }
        failureCounts.remove(normalize(api.name))
    }

    private fun successScore(api: MainAPI): Int =
        BerkStreamSettings.store?.getInt("hit_${normalize(api.name)}", 0) ?: 0

    /** Bir icerikte hangi kaynagin ise yaradigini hatirla: tekrar acilista o once denenir. */
    private fun rememberWinner(contentKey: String, apiName: String) {
        runCatching {
            BerkStreamSettings.store?.edit()?.putString("win_$contentKey", apiName)?.apply()
        }
    }

    private fun winnerFor(contentKey: String): String? =
        BerkStreamSettings.store?.getString("win_$contentKey", null)

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
        BerkStreamSettings.cacheCleaner = { shelfCache.clear(); tmdbCache.clear() }
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

    private val tmdbCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Pair<List<SearchResponse>, Boolean>>>()

    private suspend fun tmdbShelf(path: String, mediaType: String, page: Int): Pair<List<SearchResponse>, Boolean> {
        val cacheKey = "$mediaType|$path|$page"
        tmdbCache[cacheKey]
            ?.takeIf { System.currentTimeMillis() - it.first < shelfCacheMs }
            ?.let { return it.second }
        val separator = if (path.contains("?")) "&" else "?"
        // Raf kendi bolgesini belirtmisse ona dokunma: Apple TV+ katalogu TR'de
        // bos donuyor, o raf US bolgesiyle cekiliyor.
        val region = if (path.contains("watch_region=")) "" else "&watch_region=TR"
        val url = "$tmdbApiUrl/$path$separator" +
            "api_key=$tmdbApiKey&language=tr-TR&include_adult=false$region&page=$page"
        val parsed = tryParseJson<TmdbPage>(app.get(url).text) ?: return emptyList<SearchResponse>() to false
        val items = parsed.results.mapNotNull { it.toSearchResponse(mediaType) }
        val hasNext = page < (parsed.totalPages ?: 1)
        val result = items to hasNext
        if (items.isNotEmpty()) tmdbCache[cacheKey] = System.currentTimeMillis() to result
        return result
    }

    /**
     * Kendi izleme kaydimiz. CloudStream'in gecmisini yansimayla okumak her
     * surumde tutmuyor; oynatilan her basligi kendimiz de yazinca "Sana Ozel"
     * rafi kesin veriyle calisiyor.
     */
    private fun rememberWatched(title: String) {
        val store = BerkStreamSettings.store ?: return
        runCatching {
            val previous = store.getString(WATCH_HISTORY_KEY, "").orEmpty()
                .split('\n').filter { it.isNotBlank() }
            val updated = (listOf(title) + previous).distinct().take(60)
            store.edit().putString(WATCH_HISTORY_KEY, updated.joinToString("\n")).apply()
        }
    }

    private fun ownWatchedTitles(): List<String> = runCatching {
        BerkStreamSettings.store?.getString(WATCH_HISTORY_KEY, "").orEmpty()
            .split('\n').filter { it.isNotBlank() }
    }.getOrElse { emptyList() }

    /** Varsa CloudStream'in kendi gecmisi; erisilemezse sessizce bos doner. */
    private fun cloudstreamWatchedTitles(): List<String> = runCatching {
        val helper = Class.forName("com.lagradost.cloudstream3.utils.DataStoreHelper")
        val instance = helper.getField("INSTANCE").get(null)
        val ids = buildList {
            for (method in listOf("getAllResumeStateIds", "getAllWatchStateIds")) {
                runCatching {
                    (helper.getMethod(method).invoke(instance) as? List<*>)?.let(::addAll)
                }
            }
        }.filterIsInstance<Int>().distinct()
        val readers = listOf("getLastWatched", "getBookmarkedData").mapNotNull { method ->
            runCatching { helper.getMethod(method, Integer::class.java) }.getOrNull()
        }
        ids.takeLast(30).mapNotNull { id ->
            readers.firstNotNullOfOrNull { reader ->
                runCatching {
                    val record = reader.invoke(instance, id) ?: return@runCatching null
                    record.javaClass.getMethod("getName").invoke(record) as? String
                }.getOrNull()
            }
        }.filter { it.isNotBlank() }.distinct()
    }.getOrElse { emptyList() }

    private fun watchedTitles(): List<String> =
        (ownWatchedTitles() + cloudstreamWatchedTitles()).distinct()

    private fun String.encodeQuery(): String = java.net.URLEncoder.encode(this, "UTF-8")

    private suspend fun tmdbLookup(title: String): Pair<String, Int>? = runCatching {
        val url = "$tmdbApiUrl/search/multi?api_key=$tmdbApiKey&language=tr-TR" +
            "&include_adult=false&query=${title.encodeQuery()}"
        tryParseJson<TmdbPage>(app.get(url).text)?.results
            ?.firstOrNull { it.id != null && (it.mediaType == "movie" || it.mediaType == "tv") }
            ?.let { it.mediaType!! to it.id!! }
    }.getOrNull()

    /** Izlenenlere/yarim birakilanlara gore TMDB onerileri. */
    private suspend fun personalShelf(page: Int): Pair<List<SearchResponse>, Boolean> {
        val history = watchedTitles()
        val seeds = history.take(12).shuffled().take(4)
        if (seeds.isEmpty()) return tmdbShelf("trending/all/week", "mixed", page)
        val recommendations = seeds.amap { title ->
            val found = tmdbLookup(title) ?: return@amap emptyList()
            runCatching { tmdbShelf("${found.first}/${found.second}/recommendations", found.first, page).first }
                .getOrElse { emptyList() }
        }.flatten()
        val seen = history.map(::looseTitle).toSet()
        val filtered = recommendations
            .distinctBy { it.url }
            .filter { looseTitle(it.name) !in seen }
            .shuffled()
        if (filtered.isEmpty()) return tmdbShelf("trending/all/week", "mixed", page)
        return filtered to (page < 5)
    }

    /**
     * Kaynaklarin kendi ana sayfalarindaki taze icerik. TMDB'ye heniz girmemis
     * yeni bolumler burada goruntuleniyor.
     */
    private suspend fun freshFromProviders(): List<SearchResponse> {
        ensureDomains()
        val providers = validApisFor(movieTypes + seriesTypes).take(5)
        return scanProviders(providers) { api ->
            val first = api.mainPage.firstOrNull() ?: return@scanProviders null
            api.getMainPage(1, MainPageRequest(first.name, first.data, first.horizontalImages))
                ?.items?.flatMap { it.list }?.take(8)
        }.flatten().distinctBy { "${it.apiName}|${looseTitle(it.name)}" }.shuffled().take(40)
    }

    private val sportsWords = listOf(
        "spor", "sport", "bein", "tivibu", "smart", "futbol", "lig", "mac", "match",
        "eurosport", "aspor", "exxen", "s tv", "ssport", "nba", "uefa", "sampiyon",
        "super lig", "kanallar",
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

        // Spor icin once spor kategorileri denenir; hicbir kategori eslesmezse
        // kaynagin tum raflari gezilip kanal adina gore suzulur.
        val anySportsCategory = onlySports && providers.any { api ->
            api.mainPage.any { isSporty(it.name) }
        }
        return scanProviders(providers) { api ->
            val shelves = api.mainPage
                .filter { !onlySports || !anySportsCategory || isSporty(it.name) }
                .take(if (onlySports) 8 else 12)
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
                if (!onlySports) items else {
                    val byName = items.filter { isSporty(it.name) }
                    // Kanal adindan hicbir sey cikmazsa eldeki listeyi bos
                    // gostermek yerine oldugu gibi veriyoruz.
                    if (byName.size >= 5) byName else items
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
     * CloudStream arama sonuclarini **cevap verme sirasina** gore diziyor
     * (LinkedHashMap, paralel amap). Onceki surumde burada 18 kaynak taraniyordu
     * ve BerkStream satiri en son doluyordu. Artik yalnizca tek TMDB istegi
     * yapiliyor: satir ilk donenlerden biri oluyor ve listenin ustunde cikiyor.
     * Kaynaklarin kendi sonuclari zaten kendi satirlarinda listeleniyor.
     */
    override suspend fun search(query: String, page: Int): SearchResponseList? = try {
        super.search(query, page)
    } catch (error: Throwable) {
        logError(Exception(error))
        null
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
        // Linkler once toplaniyor, sonra dublaj -> altyazi sirasiyla veriliyor.
        // Callback'e geldigi sirayla verilseydi hangi kaynak once cevap verirse
        // o uste cikiyordu.
        val collected = java.util.Collections.synchronizedList(mutableListOf<ExtractorLink>())
        val countingCallback: (ExtractorLink) -> Unit = { extractor ->
            linkCount.incrementAndGet()
            collected.add(extractor)
        }

        if (BerkStreamSettings.subtitlesEnabled) loadStremioSubtitles(link, subtitleCallback)

        // Ayni icerik daha once hangi kaynaktan acildiysa o kaynak listenin
        // basina aliniyor; tekrar izlemede tarama neredeyse aninda bitiyor.
        val contentKey = "${looseTitle(title)}_${season ?: 0}_${episode ?: 0}".take(80)
        val winner = winnerFor(contentKey)
        val ordered = validApisFor(
            if (isSeries) seriesTypes else movieTypes,
            if (isSeries) TvType.Anime else TvType.AnimeMovie,
        )

        // Tek bir kaynakta: ara, dogru bolumu bul, linkleri cikar.
        suspend fun tryProvider(api: MainAPI): Boolean? {
            val results = api.searchSafely(title)
            // Once tam eslesme, sonra bulanik. "Iceren" esleme KULLANILMIYOR:
            // "Dexter" aramasi "Dexter: Resurrection" ve "Dexter's Laboratory"
            // ile eslesip yanlis icerik aciyordu.
            val hit = results.firstOrNull { looseTitle(it.name) == looseTitle(title) }
                ?: results.firstOrNull { titleMatches(it.name, title) }
                ?: return null
            val response = api.load(hit.url) ?: return null

            // Kaynaklar sezon numarasini TMDB ile ayni vermiyor; tam eslesme
            // tutmazsa bolum numarasina, en son siraya bakilir.
            fun pick(episodes: List<com.lagradost.cloudstream3.Episode>): String? {
                val match = episodes.firstOrNull {
                    (season == null || it.season == season) &&
                        (episode == null || it.episode == episode)
                }
                    ?: episodes.firstOrNull { episode != null && it.episode == episode }
                    ?: episode?.let { episodes.getOrNull(it - 1) }
                return match?.data
            }

            val innerData = when {
                // Anime kaynaklari AnimeLoadResponse donuyor ve bolumleri
                // dublaj/altyazi durumuna gore gruplanmis halde tutuyor.
                isSeries && response is AnimeLoadResponse -> {
                    // Dublaj varsa once o deneniyor; anime kaynaklari bolumleri
                    // zaten dublaj/altyazi durumuna gore ayirmis tutuyor.
                    val dubbedFirst = response.episodes.entries
                        .sortedBy { if (it.key.name.contains("Dub", true)) 0 else 1 }
                        .flatMap { it.value }
                    pick(dubbedFirst)
                }
                isSeries && response is TvSeriesLoadResponse -> pick(response.episodes)
                !isSeries && response is MovieLoadResponse -> response.dataUrl
                else -> null
            } ?: return null

            val before = linkCount.get()
            api.loadLinks(innerData, isCasting, subtitleCallback, countingCallback)
            if (linkCount.get() > before) {
                noteSuccess(api)
                rememberWinner(contentKey, api.name)
            }
            return true
        }

        // Bu icerik daha once hangi kaynaktan acildiysa once YALNIZ o deneniyor.
        // Tutarsa digerlerine hic bakilmiyor; tekrar izlemede bekleme kalkiyor.
        if (winner != null) {
            ordered.firstOrNull { it.name == winner }?.let { winnerApi ->
                scanProviders(listOf(winnerApi)) { tryProvider(it) }
                // Linkler biriktirilip sonda siralandigi icin buradan cikarken de
                // MUTLAKA gonderilmeli; aksi halde toplanan linkler hic verilmiyor
                // ve daha once acilan icerikler "baglanti bulunamadi" veriyor.
                if (linkCount.get() > 0) {
                    emitSorted(collected, callback)
                    return true
                }
            }
        }

        for (batch in ordered.filter { it.name != winner }.chunked(4)) {
            scanProviders(batch) { tryProvider(it) }
            if (linkCount.get() >= BerkStreamSettings.linkTarget) break
        }
        emitSorted(collected, callback)
        return linkCount.get() > 0
    }

    private val dubbedWords = listOf("dublaj", "dublajli", "dubbed", "turkce dublaj", "tr dublaj")
    private val subbedWords = listOf("altyazi", "altyazili", "subbed", "turkce altyazi", "tr altyazi")

    /**
     * Babanin istegi: once Turkce dublaj, sonra Turkce altyazili kaynaklar.
     * Kaynak adlari serbest metin oldugu icin etiketten anlasiliyor.
     */
    private fun linkRank(link: ExtractorLink): Int {
        val label = looseTitle("${link.name} ${link.source}")
        return when {
            dubbedWords.any { label.contains(it) } -> 0
            subbedWords.any { label.contains(it) } -> 1
            else -> 2
        }
    }

    private fun emitSorted(links: List<ExtractorLink>, callback: (ExtractorLink) -> Unit) {
        synchronized(links) { links.toList() }
            .sortedBy { linkRank(it) }
            .forEach(callback)
    }

    /**
     * Altyazi: Stremio'nun kamuya acik OpenSubtitles kopruSu anahtarsiz calisiyor
     * ve TmdbLink zaten imdbID tasiyor. Kaynak sitenin kendi altyazisi olmadigi
     * durumlarda tek secenek bu.
     */
    private suspend fun loadStremioSubtitles(link: TmdbLink, subtitleCallback: (SubtitleFile) -> Unit) {
        // DiziBal gibi kaynaklarda altyazi hic gorunmuyordu: TmdbLink'te imdbID
        // bos geldiginde altyazi istegi hic yapilmiyordu. Bos ise TMDB'den
        // cekiliyor.
        val imdbId = link.imdbID?.takeIf { it.startsWith("tt") }
            ?: link.tmdbID?.let { tmdbId ->
                val kind = if (link.season != null || link.episode != null) "tv" else "movie"
                runCatching {
                    tryParseJson<TmdbExternalIds>(
                        app.get("$tmdbApiUrl/$kind/$tmdbId/external_ids?api_key=$tmdbApiKey").text,
                    )?.imdbId?.takeIf { it.startsWith("tt") }
                }.getOrNull()
            }
            ?: return
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

    /**
     * @param guarantee bu turu destekleyen kaynaklardan birkaci, oncelik
     * siralamasinda geride kalsalar bile listeye ekleniyor. Anime kaynaklari
     * yalnizca TvType.Anime destekledigi icin genel siralamada hep disarida
     * kaliyordu.
     */
    private fun validApisFor(types: Set<TvType>, guarantee: TvType? = null): List<MainAPI> {
        val ordered = orderedApis(types)
        val head = ordered.take(18)
        if (guarantee == null) return head
        val extra = ordered.filter { guarantee in it.supportedTypes && it !in head }.take(12)
        return head + extra
    }

    private fun orderedApis(types: Set<TvType>) = apis.filter {
        it.name != name && it.lang == "tr" && it.providerType != ProviderType.MetaProvider &&
            it.supportedTypes.any(types::contains)
    }.sortedWith(
        // CI taramasi GitHub'in ABD sunucularindan yapiliyor ve Turkiye'den
        // calisan kaynaklari da olu isaretleyebiliyor. Bu yuzden o liste artik
        // eleme yapmiyor, yalnizca siralamada geri atiyor; gercek eleme cihazda
        // ust uste cevapsiz kalan kaynaklara uygulaniyor.
        compareBy<MainAPI> { normalize(it.name) in disabledProviders }
            .thenByDescending { successScore(it) }
            .thenBy { api ->
                providerPriority.indexOfFirst { it.equals(api.name, ignoreCase = true) }
                    .let { if (it == -1) Int.MAX_VALUE else it }
            },
    )

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
