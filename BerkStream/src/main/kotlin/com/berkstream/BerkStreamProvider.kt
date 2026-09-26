package com.berkstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
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
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        // Gunun trendleri ilk sirada. BerkStream uygulamasi ust afise once
        // "SANA ÖZEL", sonra populer/trend/vizyon raflarindan film seciyor.
        shelf("🔥  GÜNÜN TRENDLERİ", "mixed", "trending/all/day"),
        // BerkStream uygulamasi adinda "İLK 10" gecen raflari Netflix gibi
        // buyuk sira numarali seride gosteriyor (1, 2, 3...).
        // Turkiye'deki abonelik platformlarinda en populer olanlar; "Gunun trendleri"
        // rafiyla ayni listeyi tekrarlamasin diye ayri kaynak.
        shelf("🏆  BUGÜN İLK 10 FİLM", "movie", "discover/movie?sort_by=popularity.desc&with_watch_monetization_types=flatrate&vote_count.gte=50"),
        shelf("🏆  BUGÜN İLK 10 DİZİ", "tv", "discover/tv?sort_by=popularity.desc&with_watch_monetization_types=flatrate&vote_count.gte=50"),
        // v34 (Berk 2026-09-27: "Bu hafta vizyonda diye filmleri göstersin"): once bu hafta
        // Turkiye'de sinemaya girenler, arkasindan vizyonda olmaya devam edenler.
        MainPageData("🎬  BU HAFTA VİZYONDA", "thisweek", false),
        shelf("🎬  VİZYONDAKİ FİLMLER", "movie", "movie/now_playing?region=TR"),
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
        // Platform raflarinin sirasi TMDB'nin dunya populerligi degil: bkz. [platformShelf].
        // Apple TV+ Turkiye verisinde yok (2026-09-25: TMDB TR 0 sonuc, JustWatch TR 1 dizi),
        // o yuzden ABD katalogu ve ABD populerligi kullaniliyor.
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

    /** Tek bir scanProviders cagrisinin varsayilan butcesi (raflar icin). */
    private val providerScanBudgetMs = 18_000L

    /**
     * Oynat'a basildiktan sonra link aramanin TOPLAM ustu.
     * Bunun asilmasi kullanici icin "acilmiyor" demek; bos donmek bile
     * dakikalarca beklemekten iyi.
     */
    private val totalLinkScanBudgetMs = 30_000L

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
        @JsonProperty("vote_count") val voteCount: Int? = null,
        @JsonProperty("genre_ids") val genreIds: List<Int> = emptyList(),
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

    /** `year` [SearchResponse] arayuzunde yok, yalnizca alt siniflarda var. */
    private fun yearOf(result: SearchResponse): Int? = when (result) {
        is MovieSearchResponse -> result.year
        is TvSeriesSearchResponse -> result.year
        is AnimeSearchResponse -> result.year
        else -> null
    }

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

    private data class TmdbTitleInfo(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
    )

    /** Oynat'a basildiginda hangi adlarla aranacagi + dogrulama yili. */
    private data class TitleSet(val names: List<String>, val year: Int?)

    /**
     * Aranacak ad listesi.
     *
     * Neden gerekli: `TmdbProvider` detay cagrisini `language=en-US` ile yapiyor,
     * yani [TmdbLink.movieName] TMDB'nin INGILIZCE adi. Turkce kaynaklar ayni
     * yapimi Turkce adiyla listeliyor. Site aramada Ingilizce adi bulsa bile
     * DONDURDUGU ad Turkce oluyor ve [titleMatches] onu "baska yapim" sayip
     * atiyordu: "The Bridge on the River Kwai" arandi, site "Kwai Koprusu"
     * dondu, eslesme reddedildi, eski filmler hic acilmadi (2026-09-19 olcumu).
     * Artik Turkce ad da adaylar arasinda; eslesme adaylardan HERHANGI biriyle
     * tutarsa kabul ediliyor.
     */
    private suspend fun titleCandidates(link: TmdbLink, fallback: String, isSeries: Boolean): TitleSet {
        val id = link.tmdbID ?: return TitleSet(listOf(fallback), null)
        val kind = if (isSeries) "tv" else "movie"
        // Uygulama 2.8'den beri detaylari Turkce aliyor, fallback artik Turkce ad; yabanci
        // kaynaklar icin Ingilizce ad ayrica isteniyor (iki istek paralel).
        val (info, english) = coroutineScope {
            val tr = async {
                runCatching {
                    tryParseJson<TmdbTitleInfo>(
                        app.get("$tmdbApiUrl/$kind/$id?api_key=$tmdbApiKey&language=tr-TR").text,
                    )
                }.getOrNull()
            }
            val en = async {
                runCatching {
                    tryParseJson<TmdbTitleInfo>(
                        app.get("$tmdbApiUrl/$kind/$id?api_key=$tmdbApiKey&language=en-US").text,
                    )
                }.getOrNull()
            }
            tr.await() to en.await()
        }

        val names = listOfNotNull(
            // Turkce ad once: kaynaklarin tamami Turkce site.
            info?.title ?: info?.name,
            fallback,
            english?.title ?: english?.name,
            info?.originalTitle ?: info?.originalName,
        ).map { it.trim() }.filter { it.isNotBlank() }
            .distinctBy { looseTitle(it) }

        val year = (info?.releaseDate ?: info?.firstAirDate)?.take(4)?.toIntOrNull()
        return TitleSet(names.ifEmpty { listOf(fallback) }, year)
    }

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

    /** Calisma aninda susturulan kaynaklar: ad -> ne zamana kadar susturuldu. */
    private val mutedUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Kac GERCEK cevapsizliktan sonra kaynak gecici olarak susturulur. */
    private val failuresBeforeMute = 3

    /** Susturma suresi; dolunca kaynak kendiliginden taramaya geri girer. */
    private val muteDurationMs = 10 * 60 * 1000L

    /**
     * CI taramasi GitHub'in sunucularindan yapiliyor; Turkiye'den erisilemeyen
     * bir site oradan ayakta gorunebiliyor, yani yayimlanan liste kullanicinin
     * agini birebir yansitmiyor. Bu yuzden cihaz tarafinda da olcuyoruz: ust uste
     * GERCEKTEN cevap veremeyen kaynak bir sureligine taramadan cikarilir
     * (bkz. [noteFailure] / [isMuted]). "Bu baslik bu sitede yok" cevabi
     * ariza DEGILDIR -- bkz. [scanProviders].
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
        mutedUntil.remove(normalize(api.name))
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

    /**
     * Gercekten cevap vermeyen kaynagi gecici olarak susturur.
     *
     * Iki degisiklik (2026-09-19): esik 2'den 3'e cikti ve eleme artik KALICI
     * degil. Eskiden susturulan kaynak uygulama kapanana kadar bir daha hic
     * denenmiyordu; telefon wifi'dan 4G'ye gecerken olusan tek bir kesinti bile
     * saglam kaynaklari oturumun sonuna kadar oldurebiliyordu.
     */
    private fun noteFailure(api: MainAPI) {
        val key = normalize(api.name)
        val total = failureCounts.merge(key, 1, Int::plus) ?: 1
        if (total >= failuresBeforeMute) {
            mutedUntil[key] = System.currentTimeMillis() + muteDurationMs
        }
    }

    /** Susturma suresi dolduysa kaynak kendiliginden geri gelir. */
    private fun isMuted(key: String): Boolean {
        val until = mutedUntil[key] ?: return false
        if (System.currentTimeMillis() >= until) {
            mutedUntil.remove(key)
            failureCounts.remove(key)
            return false
        }
        return true
    }

    /**
     * Kaynak siteleri surekli adres degistirdigi icin adresler pakete gomulu
     * kalamiyor. CI haftalik tarama yapip `domains.json` uretiyor; burada o liste
     * uygulaniyor: guncel adres saglayicinin mainUrl'ine yaziliyor, cevap
     * vermeyen kaynaklar da taramaya hic sokulmuyor.
     */
    init {
        BerkStreamSettings.cacheCleaner = {
            shelfCache.clear(); tmdbCache.clear(); justWatchCache.clear(); platformShown.clear()
            tasteCache = null
        }
        BerkStreamSettings.domainRefresher = {
            domainsApplied = false
            disabledProviders.clear()
            failureCounts.clear()
            mutedUntil.clear()
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
        val parsed = tmdbPage(path, page) ?: return emptyList<SearchResponse>() to false
        val items = parsed.results.mapNotNull { it.toSearchResponse(mediaType) }
        val hasNext = page < (parsed.totalPages ?: 1)
        val result = items to hasNext
        if (items.isNotEmpty()) tmdbCache[cacheKey] = System.currentTimeMillis() to result
        return result
    }

    /**
     * "Bu hafta vizyonda": Turkiye'de son 9 gunde (gecen cumadan beri) sinemaya giren filmler,
     * populerlige gore; ardindan vizyonda kalanlar (TMDB now_playing TR). Tarihler TR vizyon
     * tarihi (region=TR + with_release_type=3), filmin dunya prömiyeri degil.
     */
    private suspend fun thisWeekInCinemas(): List<SearchResponse> {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val now = java.util.Calendar.getInstance()
        val to = fmt.format(now.time)
        now.add(java.util.Calendar.DAY_OF_YEAR, -9)
        val from = fmt.format(now.time)
        val fresh = tmdbPage(
            "discover/movie?region=TR&with_release_type=3&release_date.gte=$from&release_date.lte=$to&sort_by=popularity.desc",
            1,
        )?.results.orEmpty()
        val running = (1..2).flatMap { tmdbPage("movie/now_playing?region=TR", it)?.results.orEmpty() }
        return (fresh + running).distinctBy { it.id }
            .mapNotNull { it.toSearchResponse("movie") }
            .take(30)
    }

    private suspend fun tmdbPage(path: String, page: Int): TmdbPage? {
        val separator = if (path.contains("?")) "&" else "?"
        // Raf kendi bolgesini belirtmisse ona dokunma: Apple TV+ katalogu TR'de
        // bos donuyor, o raf US bolgesiyle cekiliyor.
        val region = if (path.contains("watch_region=")) "" else "&watch_region=TR"
        val url = "$tmdbApiUrl/$path$separator" +
            "api_key=$tmdbApiKey&language=tr-TR&include_adult=false$region&page=$page"
        return tryParseJson<TmdbPage>(app.get(url).text)
    }

    // ---- Platform raflarinin siralamasi (Netflix, Prime, Disney+, HBO Max, Apple TV+, tabii) ----
    //
    // Eskiden bu raflar sort_by vermeden TMDB'ye gidiyordu; TMDB de kendi varsayilani olan
    // DUNYA populerligiyle siraliyordu, oy tabani da yoktu. Simdi her icerik bir skor aliyor:
    //   0.40 Turkiye'de o platformdaki populerlik (JustWatch TR sirasi)
    //   0.25 zevk uyumu (izleme gecmisindeki turler)
    //   0.20 kalite (az oylu puan sisirilmesin diye bayes ortalamasi)
    //   0.15 yenilik (cikis tarihi)
    // Izlenmis icerik rafin sonuna iner; "Devam Et" zaten onu gosteriyor.

    /** TMDB saglayici kimligi -> (JustWatch paket kodu, ulke). Kodlar 2026-09-25'te canli sorguyla dogrulandi. */
    private val justWatchPackages = mapOf(
        "8" to ("nfx" to "TR"),
        "119" to ("prv" to "TR"),
        "337" to ("dnp" to "TR"),
        "1899" to ("mxx" to "TR"),
        "2235" to ("tab" to "TR"),
        "350" to ("atp" to "US"),
    )

    private val justWatchUrl = "https://apis.justwatch.com/graphql"
    private val justWatchTtlMs = 6 * 60 * 60 * 1000L

    private data class JwResponse(@JsonProperty("data") val data: JwData? = null)
    private data class JwData(@JsonProperty("popularTitles") val popularTitles: JwTitles? = null)
    private data class JwTitles(@JsonProperty("edges") val edges: List<JwEdge> = emptyList())
    private data class JwEdge(@JsonProperty("node") val node: JwNode? = null)
    private data class JwNode(@JsonProperty("content") val content: JwContent? = null)
    private data class JwIds(@JsonProperty("tmdbId") val tmdbId: String? = null)
    private data class JwScoring(
        @JsonProperty("imdbScore") val imdbScore: Double? = null,
        @JsonProperty("imdbVotes") val imdbVotes: Double? = null,
    )
    private data class JwContent(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("originalReleaseYear") val year: Int? = null,
        @JsonProperty("posterUrl") val posterUrl: String? = null,
        @JsonProperty("externalIds") val externalIds: JwIds? = null,
        @JsonProperty("scoring") val scoring: JwScoring? = null,
    )

    private data class ChartEntry(
        val tmdbId: Int,
        val title: String,
        val year: Int?,
        val poster: String?,
        val imdbScore: Double?,
        val imdbVotes: Double?,
    )

    private val justWatchCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<ChartEntry>>>()

    /** Platform raflarinin ilk sayfasinda gosterilen kartlar; sonraki sayfalar bunlari tekrarlamasin. */
    private val platformShown = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

    @Volatile
    private var tasteCache: Pair<String, Map<Int, Double>>? = null

    /** Bir platformun o ulkedeki gunluk populerlik sirasi, en fazla 40 baslik. */
    private suspend fun justWatchChart(pkg: String, country: String, isShow: Boolean): List<ChartEntry> {
        val key = "$country|$pkg|$isShow"
        justWatchCache[key]
            ?.takeIf { System.currentTimeMillis() - it.first < justWatchTtlMs }
            ?.let { return it.second }
        val query = "query BerkPlatform(\$filter: TitleFilter) { popularTitles(country: $country, " +
            "first: 40, filter: \$filter, sortBy: POPULAR) { edges { node { content(country: $country, " +
            "language: tr) { title originalReleaseYear posterUrl externalIds { tmdbId } " +
            "scoring { imdbScore imdbVotes } } } } } }"
        val body = mapOf(
            "query" to query,
            "variables" to mapOf(
                "filter" to mapOf(
                    "objectTypes" to listOf(if (isShow) "SHOW" else "MOVIE"),
                    "packages" to listOf(pkg),
                ),
            ),
        )
        val entries = runCatching {
            val text = app.post(
                justWatchUrl,
                json = body,
                headers = mapOf("Content-Type" to "application/json"),
                timeout = 10L,
            ).text
            tryParseJson<JwResponse>(text)?.data?.popularTitles?.edges.orEmpty().mapNotNull { edge ->
                val content = edge.node?.content ?: return@mapNotNull null
                val id = content.externalIds?.tmdbId?.toIntOrNull() ?: return@mapNotNull null
                val title = content.title?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ChartEntry(
                    tmdbId = id,
                    title = title,
                    year = content.year,
                    poster = content.posterUrl
                        ?.replace("{profile}", "s332")?.replace("{format}", "webp")
                        ?.let { "https://images.justwatch.com$it" },
                    imdbScore = content.scoring?.imdbScore,
                    imdbVotes = content.scoring?.imdbVotes,
                )
            }.distinctBy { it.tmdbId }
        }.getOrElse {
            logError(Exception("JustWatch sirasi alinamadi: $key", it))
            emptyList()
        }
        if (entries.isNotEmpty()) justWatchCache[key] = System.currentTimeMillis() to entries
        return entries
    }

    /** Dizi turleri film karsiliklarina cevriliyor; "Aksiyon & Macera" dizisi aksiyon filmi sevene de uyar. */
    private fun canonicalGenres(ids: List<Int>): List<Int> = ids.flatMap {
        when (it) {
            10759 -> listOf(28, 12)
            10765 -> listOf(878, 14)
            10768 -> listOf(10752)
            else -> listOf(it)
        }
    }.distinct()

    /** Izlenen basliklarin turlerinden zevk profili: tur -> 0..1. Yeni izlenen daha agir basar. */
    private suspend fun tasteProfile(): Map<Int, Double> {
        val history = watchedTitles().take(15)
        if (history.isEmpty()) return emptyMap()
        val key = history.joinToString("\n")
        tasteCache?.takeIf { it.first == key }?.let { return it.second }
        val genresPerTitle = history.amap { title ->
            runCatching {
                val url = "$tmdbApiUrl/search/multi?api_key=$tmdbApiKey&language=tr-TR" +
                    "&include_adult=false&query=${title.encodeQuery()}"
                tryParseJson<TmdbPage>(app.get(url).text)?.results
                    ?.firstOrNull { it.mediaType == "movie" || it.mediaType == "tv" }
                    ?.genreIds.orEmpty()
            }.getOrElse { emptyList() }
        }
        val weights = HashMap<Int, Double>()
        genresPerTitle.forEachIndexed { index, genres ->
            val recency = 1.0 / (1.0 + index * 0.15)
            canonicalGenres(genres).forEach { weights.merge(it, recency, Double::plus) }
        }
        val max = weights.values.maxOrNull() ?: return emptyMap()
        val profile = weights.mapValues { it.value / max }
        tasteCache = key to profile
        return profile
    }

    /** Az oylu yapimin 9.5'i tek basina bir sey soylemez; oy sayisi azsa puan ortalamaya cekilir. */
    private fun bayesQuality(score: Double?, votes: Double?, minVotes: Double): Double? {
        if (score == null || score <= 0.0 || votes == null || votes <= 0.0) return null
        val shrunk = (votes * score + minVotes * 6.3) / (votes + minVotes)
        return ((shrunk - 5.0) / 3.5).coerceIn(0.0, 1.0)
    }

    private val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

    private fun recencyOf(date: String?, year: Int?): Double {
        val millis = date?.takeIf { it.length >= 10 }
            ?.let { runCatching { synchronized(isoDate) { isoDate.parse(it.take(10)) }?.time }.getOrNull() }
            ?: year?.let { runCatching { synchronized(isoDate) { isoDate.parse("$it-07-01") }?.time }.getOrNull() }
            ?: return 0.0
        val days = (System.currentTimeMillis() - millis) / 86_400_000.0
        return (1.0 - days.coerceAtLeast(0.0) / 540.0).coerceIn(0.0, 1.0)
    }

    private fun ChartEntry.toSearchResponse(isShow: Boolean): SearchResponse {
        val kind = if (isShow) "tv" else "movie"
        val url = "https://www.themoviedb.org/$kind/$tmdbId"
        return if (isShow) {
            newTvSeriesSearchResponse(title, url, TvType.TvSeries, false) {
                this.id = tmdbId; this.posterUrl = poster; this.year = this@toSearchResponse.year
            }
        } else {
            newMovieSearchResponse(title, url, TvType.Movie, false) {
                this.id = tmdbId; this.posterUrl = poster; this.year = this@toSearchResponse.year
            }
        }
    }

    /**
     * Yalnizca duz platform rafi ("discover/movie?with_watch_providers=8") JustWatch sirasiyla
     * karistirilir. Uygulamanin platform sayfasindaki tur/yeni/puan raflari kendi filtresiyle
     * gelir; onlara Turkiye Ilk 10'unu karistirmak, "Korku" rafina komedi sokmak demekti.
     */
    private fun isPlainPlatform(path: String): Boolean =
        path.contains("with_watch_providers") &&
            listOf("with_genres", "sort_by", "with_original_language", "vote_average", "release_date", "air_date")
                .none { path.contains(it) }

    /** "chart:nfx:TR": platformun o ulkedeki bugunku populerlik sirasi, uygulamada Ilk 10 serisi. */
    private suspend fun platformChart(path: String, isShow: Boolean, page: Int): Pair<List<SearchResponse>, Boolean> {
        if (page > 1) return emptyList<SearchResponse>() to false
        val parts = path.split(':')
        val pkg = parts.getOrNull(1) ?: return emptyList<SearchResponse>() to false
        val country = parts.getOrNull(2) ?: "TR"
        val chart = justWatchChart(pkg, country, isShow)
        // Afis TMDB'den (Turkce), yoksa JustWatch'in kendi afisi.
        val cards = chart.take(12).amap { entry ->
            val kind = if (isShow) "tv" else "movie"
            val item = runCatching {
                tryParseJson<TmdbItem>(
                    app.get("$tmdbApiUrl/$kind/${entry.tmdbId}?api_key=$tmdbApiKey&language=tr-TR").text
                )
            }.getOrNull()
            item?.copy(id = entry.tmdbId)?.toSearchResponse(kind) ?: entry.toSearchResponse(isShow)
        }
        return cards to false
    }

    private suspend fun platformShelf(path: String, mediaType: String, page: Int): Pair<List<SearchResponse>, Boolean> {
        val providerId = Regex("with_watch_providers=(\\d+)").find(path)?.groupValues?.get(1)
        val platform = providerId?.let { justWatchPackages[it] }
        // Oy tabani: 3 oylu bilinmedik yapimlar rafa girmesin.
        val flooredPath = if (path.contains("vote_count")) path else "$path&vote_count.gte=30"
        if (platform == null) return tmdbShelf(flooredPath, mediaType, page)

        if (page > 1) {
            // Ilk sayfa TMDB'nin 1-2. sayfalarini kullandi; devami 3. sayfadan.
            val (items, hasNext) = tmdbShelf(flooredPath, mediaType, page + 1)
            val shown = platformShown[path].orEmpty()
            return items.filter { it.url !in shown } to hasNext
        }

        val cacheKey = "platform|$mediaType|$path"
        cachedShelf(cacheKey)?.let { return it to true }

        val isShow = mediaType == "tv"
        val (pkg, country) = platform
        val (chart, tmdbItems, taste) = coroutineScope {
            val chartJob = async { justWatchChart(pkg, country, isShow) }
            val pageJobs = (1..2).map { p -> async { runCatching { tmdbPage(flooredPath, p) }.getOrNull() } }
            val tasteJob = async { runCatching { tasteProfile() }.getOrElse { emptyMap() } }
            Triple(
                chartJob.await(),
                pageJobs.flatMap { it.await()?.results.orEmpty() }
                    .filter { it.id != null && it.adult != true }
                    .distinctBy { it.id },
                tasteJob.await(),
            )
        }

        val chartRank = chart.withIndex().associate { it.value.tmdbId to it.index }
        val chartById = chart.associateBy { it.tmdbId }
        val tmdbRank = tmdbItems.withIndex().associate { it.value.id!! to it.index }
        val tmdbById = tmdbItems.associateBy { it.id!! }
        val seen = watchedTitles().map(::looseTitle).toSet()

        val ranked = (chart.map { it.tmdbId } + tmdbItems.map { it.id!! }).distinct().mapNotNull { id ->
            val item = tmdbById[id]
            val entry = chartById[id]
            val card = item?.toSearchResponse(mediaType) ?: entry?.toSearchResponse(isShow) ?: return@mapNotNull null

            // Turkiye sirasinda olan her zaman olmayandan once gelir (1.0 .. 0.4);
            // olmayanlar TMDB sirasiyla 0.35'in altinda paylasir.
            val popularity = chartRank[id]?.let { 1.0 - 0.6 * it / chart.size }
                ?: tmdbRank[id]?.let { 0.35 * (1.0 - it.toDouble() / tmdbItems.size) }
                ?: 0.0
            val quality = bayesQuality(item?.voteAverage, item?.voteCount?.toDouble(), 150.0)
                ?: bayesQuality(entry?.imdbScore, entry?.imdbVotes, 1500.0)
                ?: 0.5
            val genres = canonicalGenres(item?.genreIds.orEmpty())
            val tasteFit = if (taste.isEmpty() || genres.isEmpty()) 0.5 else {
                val fits = genres.map { taste[it] ?: 0.0 }
                ((fits.maxOrNull() ?: 0.0) + fits.average()) / 2.0
            }
            val recency = recencyOf(
                if (isShow) item?.firstAirDate else item?.releaseDate,
                entry?.year,
            )
            var score = 0.40 * popularity + 0.25 * tasteFit + 0.20 * quality + 0.15 * recency
            if (looseTitle(card.name) in seen) score -= 1.0
            card to score
        }.sortedByDescending { it.second }.map { it.first }.take(40)

        if (ranked.isEmpty()) return tmdbShelf(flooredPath, mediaType, page)
        platformShown[path] = ranked.map { it.url }.toSet()
        putShelf(cacheKey, ranked)
        return ranked to true
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
                    !isMuted(normalize(api.name)) &&
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
            "thisweek" -> {
                if (page > 1) return newHomePageResponse(request, emptyList<SearchResponse>(), false)
                cachedShelf("thisweek")?.let { return newHomePageResponse(request, it, false) }
                val items = try {
                    thisWeekInCinemas()
                } catch (error: Throwable) {
                    logError(Exception(error))
                    emptyList()
                }
                if (items.isNotEmpty()) putShelf("thisweek", items)
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
            when {
                // Uygulamanin platform sayfalari (v33): "chart:nfx:TR" = o platformun bugunku Ilk 10'u.
                path.startsWith("chart:") -> platformChart(path, mediaType == "tv", page)
                isPlainPlatform(path) -> platformShelf(path, mediaType, page)
                else -> tmdbShelf(path, mediaType, page)
            }
        } catch (error: Throwable) {
            logError(Exception(error))
            emptyList<SearchResponse>() to false
        }
        return newHomePageResponse(request, items, hasNext)
    }

    private data class TmdbOverview(
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("videos") val videos: TmdbVideos? = null,
    )

    private data class TmdbVideos(
        @JsonProperty("results") val results: List<TmdbVideo> = emptyList(),
    )

    private data class TmdbVideo(
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("site") val site: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("iso_639_1") val language: String? = null,
        @JsonProperty("official") val official: Boolean? = null,
    )

    /** Once Turkce fragman, sonra Ingilizce; tur olarak Trailer > Teaser > digerleri. */
    private fun TmdbVideos.youtubeTrailers(): List<String> = results
        .filter { it.site.equals("YouTube", true) && !it.key.isNullOrBlank() }
        .filter { it.type == "Trailer" || it.type == "Teaser" }
        .sortedWith(
            compareBy<TmdbVideo>(
                { if (it.language == "tr") 0 else 1 },
                { if (it.type == "Trailer") 0 else 1 },
                { if (it.official == true) 0 else 1 },
            )
        )
        .map { "https://www.youtube.com/watch?v=${it.key}" }
        .distinct()

    /**
     * TmdbProvider detay cagrisini `language=en-US` ile yapiyor, bu yuzden ozetler
     * Ingilizce geliyordu. Turkce ozet ayrica cekilip uzerine yaziliyor.
     */
    override suspend fun load(url: String): LoadResponse? = coroutineScope {
        // Turkce ozet + fragman istegi, TMDB detay cagrisiyla ayni anda gider;
        // icerik sayfasi iki istegin toplami yerine en yavasi kadar bekler.
        val match = Regex("""themoviedb\.org/(movie|tv)/(\d+)""").find(url)
        val turkishRequest = match?.let {
            val kind = it.groupValues[1]
            val id = it.groupValues[2]
            async {
                runCatching {
                    tryParseJson<TmdbOverview>(
                        app.get(
                            "$tmdbApiUrl/$kind/$id?api_key=$tmdbApiKey&language=tr-TR" +
                                "&append_to_response=videos&include_video_language=tr,en,null",
                        ).text,
                    )
                }.getOrNull()
            }
        }
        val base = super.load(url) ?: return@coroutineScope null
        // A slow translation/videos endpoint should not hold the details page hostage.
        // Usually it has already completed in parallel with super.load().
        val turkish = withTimeoutOrNull(600L) { turkishRequest?.await() }
        if (turkish == null) turkishRequest?.cancel()
        turkish?.overview?.takeIf { it.isNotBlank() }?.let { base.plot = it }
        // Ana sayfa ve detay fragmani icin Turkce fragman one alinir.
        val trailers = turkish?.videos?.youtubeTrailers().orEmpty()
        if (trailers.isNotEmpty()) {
            val previous = base.trailers.toList()
            base.trailers.clear()
            base.addTrailer(trailers)
            previous.filter { old -> base.trailers.none { it.extractorUrl == old.extractorUrl } }
                .forEach { base.trailers.add(it) }
        }
        base
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        if (query.length < 2) return emptyList()
        return tmdbSearch(query, 1).take(12)
    }

    /**
     * CloudStream arama sonuclarini **cevap verme sirasina** gore diziyor
     * (LinkedHashMap, paralel amap). Onceki surumde burada 18 kaynak taraniyordu
     * ve BerkStream satiri en son doluyordu. Artik yalnizca tek TMDB istegi
     * yapiliyor: satir ilk donenlerden biri oluyor ve listenin ustunde cikiyor.
     * Kaynaklarin kendi sonuclari zaten kendi satirlarinda listeleniyor.
     */
    override suspend fun search(query: String, page: Int): SearchResponseList? = try {
        newSearchResponseList(tmdbSearch(query, page), page < 3)
    } catch (error: Throwable) {
        logError(Exception(error))
        null
    }

    private data class TmdbSearchItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("adult") val adult: Boolean? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("vote_count") val voteCount: Int? = null,
        @JsonProperty("popularity") val popularity: Double? = null,
    )

    private data class TmdbSearchPage(@JsonProperty("results") val results: List<TmdbSearchItem> = emptyList())

    /**
     * Turkce adlarla ve alakaya gore arama (v33). Eskiden TmdbProvider'in Ingilizce aramasi
     * TMDB'nin sirasini oldugu gibi veriyordu: "top gun" icin Top Gun'in altinda adinda "top"
     * ya da "gun" gecen, 3 oylu, yabanci afisli yapimlar (Berk, 2026-09-25).
     *
     * Siralama: ad (Turkce ya da orijinal) aranani tam karsiliyor > onunla basliyor > butun
     * kelimeleri iceriyor; esitlikte populerlik x oy. Adi uymayanlar, uyan varken atilir;
     * hic bilinmeyen (az oylu, populerligi yok) yapimlar da uyan cok varken atilir.
     */
    private suspend fun tmdbSearch(query: String, page: Int): List<SearchResponse> {
        val url = "$tmdbApiUrl/search/multi?api_key=$tmdbApiKey&language=tr-TR&include_adult=false" +
            "&page=$page&query=${query.encodeQuery()}"
        val results = tryParseJson<TmdbSearchPage>(app.get(url).text)?.results.orEmpty()
            .filter { (it.mediaType == "movie" || it.mediaType == "tv") && it.adult != true && it.id != null }
        val words = looseTitle(query).split(' ').filter { it.isNotBlank() }
        val phrase = words.joinToString(" ")
        fun match(name: String?): Int {
            if (name.isNullOrBlank() || words.isEmpty()) return 0
            val tokens = looseTitle(name).split(' ').filter { it.isNotBlank() }
            val joined = tokens.joinToString(" ")
            return when {
                joined == phrase -> 3
                joined.startsWith(phrase) -> 2
                words.all { w -> tokens.any { it.startsWith(w) } } -> 1
                else -> 0
            }
        }
        val scored = results.map { item ->
            val label = if (item.mediaType == "tv") item.name ?: item.title else item.title ?: item.name
            val original = if (item.mediaType == "tv") item.originalName else item.originalTitle
            val relevance = maxOf(match(label), match(original))
            val fame = (item.popularity ?: 0.0) * kotlin.math.ln(2.0 + (item.voteCount ?: 0))
            Triple(item, relevance, fame)
        }
        val matching = scored.filter { it.second > 0 }
        val pool = if (matching.isEmpty()) scored else matching
        val known = pool.filter { (item, relevance, _) ->
            relevance == 3 || (item.voteCount ?: 0) >= 10 || (item.popularity ?: 0.0) >= 3.0
        }
        val kept = if (known.size >= 3) known else pool
        return kept
            .sortedWith(compareByDescending<Triple<TmdbSearchItem, Int, Double>> { it.second }.thenByDescending { it.third })
            .mapNotNull { (item, _, _) ->
                TmdbItem(
                    id = item.id, title = item.title, name = item.name, posterPath = item.posterPath,
                    mediaType = item.mediaType, releaseDate = item.releaseDate, firstAirDate = item.firstAirDate,
                    adult = item.adult, voteAverage = item.voteAverage, voteCount = item.voteCount,
                ).toSearchResponse("mixed")
            }
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
        val titles = titleCandidates(link, title, isSeries)

        val linkCount = AtomicInteger(0)
        // Pass each playable link through immediately: the player can begin
        // buffering while other sites scan. The app caches and deduplicates links.
        val countingCallback: (ExtractorLink) -> Unit = { extractor ->
            linkCount.incrementAndGet()
            // newExtractorLink's constructor is suspend but has no network wait;
            // callbacks from provider scrapers are ordinary (non-suspend) functions.
            callback(runBlocking { prioritizeLink(extractor) })
        }

        // Subtitle discovery is independent of video discovery. A slow subtitle
        // endpoint must not delay the first frame on a TV box.
        val subtitleJob = if (BerkStreamSettings.subtitlesEnabled) {
            CoroutineScope(Dispatchers.IO).launch {
                withTimeoutOrNull(4_000L) { loadStremioSubtitles(link, subtitleCallback) }
            }
        } else null

        // Ayni icerik daha once hangi kaynaktan acildiysa o kaynak listenin
        // basina aliniyor; tekrar izlemede tarama neredeyse aninda bitiyor.
        val contentKey = "${looseTitle(title)}_${season ?: 0}_${episode ?: 0}".take(80)
        // Dizide bolum bazli kayit yoksa dizinin son acildigi kaynak: sonraki bolum ayni
        // siteden, ayni dublaj/kaliteyle baslasin (Berk, 2026-09-25).
        val seriesKey = "${looseTitle(title)}_series".take(80)
        val winner = winnerFor(contentKey) ?: if (isSeries) winnerFor(seriesKey) else null
        val ordered = validApisFor(
            if (isSeries) seriesTypes else movieTypes,
            if (isSeries) TvType.Anime else TvType.AnimeMovie,
        )

        // Tek bir kaynakta: ara, dogru bolumu bul, linkleri cikar.
        suspend fun tryProvider(api: MainAPI): Boolean? {
            // Yil uyusmuyorsa bu baska yapim. Diller arasi esleme acildigi icin
            // sart oldu: "Avci" aramasi 1978 yapimini da 2024 yapimini da
            // getiriyor ve ikisi de ada birebir uyuyor.
            fun yearFits(candidate: SearchResponse): Boolean {
                val want = titles.year ?: return true
                val got = yearOf(candidate) ?: return true
                return kotlin.math.abs(got - want) <= 1
            }

            // Once tam eslesme, sonra bulanik. "Iceren" esleme KULLANILMIYOR:
            // "Dexter" aramasi "Dexter: Resurrection" ve "Dexter's Laboratory"
            // ile eslesip yanlis icerik aciyordu.
            fun pickHit(results: List<SearchResponse>): SearchResponse? {
                val usable = results.filter(::yearFits)
                for (name in titles.names) {
                    usable.firstOrNull { looseTitle(it.name) == looseTitle(name) }?.let { return it }
                }
                for (name in titles.names) {
                    usable.firstOrNull { titleMatches(it.name, name) }?.let { return it }
                }
                return null
            }

            // Ilk ad genelde yetiyor (Turkce siteler Ingilizce adi da indeksliyor);
            // bulamazsa diger adlarla tekrar araniyor.
            var hit: SearchResponse? = null
            for (query in titles.names) {
                hit = pickHit(api.searchSafely(query))
                if (hit != null) break
            }
            if (hit == null) return null
            val response = api.load(hit.url) ?: return null

            // Arama sonucu yil tasimiyorsa dogrulama ancak burada yapilabiliyor.
            val wantYear = titles.year
            val gotYear = response.year
            if (wantYear != null && gotYear != null && kotlin.math.abs(gotYear - wantYear) > 1) {
                return null
            }

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
            api.loadLinks(innerData, isCasting, subtitleCallback) { link -> countingCallback(withSite(link, api.name)) }
            if (linkCount.get() > before) {
                noteSuccess(api)
                rememberWinner(contentKey, api.name)
                if (isSeries) rememberWinner(seriesKey, api.name)
            }
            return true
        }

        // Bu icerik daha once hangi kaynaktan acildiysa once YALNIZ o deneniyor.
        // Tutarsa digerlerine hic bakilmiyor; tekrar izlemede bekleme kalkiyor.
        if (winner != null) {
            ordered.firstOrNull { it.name == winner }?.let { winnerApi ->
                scanProviders(listOf(winnerApi), providerScanTimeoutMs + 2_000L) { tryProvider(it) }
                // Linkler biriktirilip sonda siralandigi icin buradan cikarken de
                // MUTLAKA gonderilmeli; aksi halde toplanan linkler hic verilmiyor
                // ve daha once acilan icerikler "baglanti bulunamadi" veriyor.
                if (linkCount.get() > 0) {
                    subtitleJob?.join()
                    return true
                }
            }
        }

        // Obek obek ve SIRAYLA taraniyordu; her obegin kendi 18 sn'lik butcesi
        // vardi ve 30 kaynak / 4 = 8 tur ediyordu. Hicbir kaynakta olmayan bir
        // baslikta (eski filmler) hicbir tur erken kesilmiyor, kullanici
        // ~70 saniye bekleyip "baglanti bulunamadi" goruyordu. Artik TEK bir
        // kuresel son tarih var ve obekler genisledi: en kotu ihtimal 30 sn.
        val deadline = System.currentTimeMillis() + totalLinkScanBudgetMs
        for (batch in ordered.filter { it.name != winner }.chunked(6)) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 1_000L) break
            scanProviders(batch, remaining) { tryProvider(it) }
            if (linkCount.get() >= BerkStreamSettings.linkTarget) break
            // Elde oynatilabilir link varken kalan kaynaklari kovalamiyoruz;
            // hedefe ulasmak icin 20 sn daha beklemek kullaniciya "acilmiyor"
            // gibi geliyor.
            if (linkCount.get() > 0 &&
                System.currentTimeMillis() > deadline - totalLinkScanBudgetMs / 2
            ) {
                break
            }
        }
        // The player already received video callbacks, so subtitles can finish
        // without delaying the first frame; join before the caller closes its callback.
        subtitleJob?.join()
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

    /**
     * CloudStream linkleri `sortUrls` ile YALNIZCA kaliteye gore siraliyor
     * (`urls.sortedBy { -it.quality }`), gonderim sirasi yok sayiliyor. Bu yuzden
     * "once dublaj" istegi sadece siralamayla karsilanamiyor: dublaj linkinin
     * kalite degeri en uste tasiniyor, gercek kalite ad icinde korunuyor.
     * Ayardan kapatilabiliyor.
     */
    /**
     * Linkin hangi siteden geldigi (DiziYou, Dizilla...) kaynak listesinde gorunsun ve uygulama
     * bir dizide hangi sitede izlendigini hatirlayabilsin diye `source` site adi olur; oynatici
     * adinin basinda da site yazar. Extractor adi (Vidmoly...) isimde kalir.
     */
    private fun withSite(link: ExtractorLink, site: String): ExtractorLink {
        if (link.source == site) return link
        return runCatching {
            runBlocking {
                newExtractorLink(
                    source = site,
                    name = if (link.name.contains(site, ignoreCase = true)) link.name else "$site • ${link.name}",
                    url = link.url,
                    type = link.type,
                ) {
                    this.quality = link.quality
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                    this.audioTracks = link.audioTracks
                }
            }
        }.getOrElse { link }
    }

    private suspend fun prioritizeLink(link: ExtractorLink): ExtractorLink {
        val boost = BerkStreamSettings.preferTurkishDub
        val rank = linkRank(link)
        if (!boost || rank > 1) return link
        // The player sorts by quality, not callback order; keep the Turkish boost.
        return runCatching {
            val realQuality = Qualities.getStringByInt(link.quality)
            val label = if (rank == 0) "🇹🇷 Dublaj" else "🇹🇷 Altyazı"
            newExtractorLink(
                source = link.source,
                name = "$label • ${link.name} ($realQuality)",
                url = link.url,
                type = link.type,
            ) {
                this.quality = Qualities.P2160.value + if (rank == 0) 100 else 50
                this.referer = link.referer
                this.headers = link.headers
                this.extractorData = link.extractorData
            }
        }.getOrElse { link }
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
        compareBy<MainAPI> { isMuted(normalize(it.name)) }
            .thenBy { normalize(it.name) in disabledProviders }
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
        budgetMs: Long = providerScanBudgetMs,
        block: suspend (MainAPI) -> T?,
    ): List<T> = withTimeoutOrNull(budgetMs.coerceAtLeast(1_000L)) {
        providers.amap { api ->
            try {
                // Sarmalayici SART: `withTimeoutOrNull` zaman asiminda da null
                // doner, `block` "bu baslik bu sitede yok" dediginde de null
                // doner. Ayrim yapilmadigi icin her basarili ama sonucsuz arama
                // ariza sayiliyordu -- hicbir Turkce kaynakta butun katalog
                // olmadigindan iki film actiktan sonra saglayicilarin neredeyse
                // tamami susturuluyordu (2026-09-09'da girdi, "artik hicbir sey
                // acilmiyor"un sebebi buydu). Tek elemanli liste ile sariyoruz:
                // liste null ise GERCEKTEN zaman asimi, listenin ici null ise
                // sadece sonuc yok.
                val boxed = withTimeoutOrNull(providerScanTimeoutMs) { listOf(block(api)) }
                if (boxed == null) {
                    noteFailure(api)
                    null
                } else {
                    boxed.firstOrNull()
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
