package com.berkstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Anime icin dogrudan yol (v37, Berk 2026-09-27: "animelerden gelen kaynak sikintili,
 * kaliteli guzel kaynaktan alt yazili gelsin").
 *
 * Eskiden anime de 60 sitelik genel taramaya giriyordu: anime siteleri oncelik listesinin
 * sonundaydi, genel siteler (dizi/film) once bitene kadar bekleniyordu ve gomulu AnimeciX
 * saglayicisi dogru bolumu bulmak icin dizinin BUTUN bolumlerini indiriyordu (One Piece:
 * tek istekte 7,4 MB). TV kutusunda zaman asimina dusuyordu.
 *
 * AnimeciX'in API'si her yapimin TMDB kimligini veriyor; eslesme ada degil kimlige bakiyor
 * ("One Piece" ile "One Piece (Live Action)" karismaz). Bolum icin tek istek yeterli:
 * `best-video` o bolumun en iyi yuklemesine (tau-video) yonlendiriyor, oradan 480p/720p/1080p
 * MP4 geliyor. Videolar fansub: Turkce altyazi goruntuye gomulu (2026-09-27, JJK 2x05 karesi).
 *
 * Sezon farki: TMDB ile AnimeciX sezonlari ayni bolmuyor (JJK: TMDB 1 sezon 59 bolum,
 * AnimeciX 3 sezon; One Piece: TMDB 23 sezon, AnimeciX tek sezon 1155 bolum). Bolum TMDB'deki
 * mutlak sirasina cevrilip AnimeciX'in kendi sezonlarina dagitiliyor.
 */
internal object AnimeSource {
    const val NAME = "AnimeciX"
    private const val MAIN = "https://animecix.tv"
    private const val REFERER = "$MAIN/"
    private const val TAU = "https://tau-video.xyz"

    /** Sitenin kendi istemcisinin gonderdigi baslik; `titles` bunsuz bos donuyor. */
    private val apiHeaders = mapOf(
        "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk",
    )

    /** Animasyon + Japon/Cin/Kore yapimi = anime (TMDB "Animation" turu 16). */
    fun isAnime(originalLanguage: String?, genreIds: Collection<Int>): Boolean =
        16 in genreIds && originalLanguage in setOf("ja", "zh", "ko")

    private data class Hit(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("tmdb_id") val tmdbId: Int? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("season_count") val seasonCount: Int? = null,
        @JsonProperty("episode_count") val episodeCount: Int? = null,
    )

    private data class SearchPage(@JsonProperty("results") val results: List<Hit> = emptyList())

    private data class Season(
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("episode_count") val episodeCount: Int? = null,
    )

    private data class Video(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("season_num") val seasonNum: Int? = null,
        @JsonProperty("episode_num") val episodeNum: Int? = null,
    )

    private data class TitleBody(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("seasons") val seasons: List<Season> = emptyList(),
        @JsonProperty("videos") val videos: List<Video> = emptyList(),
    )

    private data class TitlePage(@JsonProperty("title") val title: TitleBody? = null)
    private data class VideoPage(@JsonProperty("videos") val videos: List<Video> = emptyList())

    private data class TauUrl(
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("url") val url: String? = null,
    )

    private data class TauVideo(@JsonProperty("urls") val urls: List<TauUrl> = emptyList())

    /** tmdb kimligi + tur -> AnimeciX kimligi (oturum boyu; ayni dizide bolum gecisi aramasiz). */
    private val idCache = ConcurrentHashMap<String, Int>()

    /** AnimeciX kimligi -> sezon bolum sayilari (gercek video listesinden sayilmis). */
    private val seasonCache = ConcurrentHashMap<Int, List<Pair<Int, Int>>>()

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private suspend fun findId(tmdbId: Int, names: List<String>, isSeries: Boolean): Int? {
        val key = "${if (isSeries) "tv" else "movie"}:$tmdbId"
        idCache[key]?.let { return it }
        for (name in names.take(3)) {
            val hits = runCatching {
                tryParseJson<SearchPage>(app.get("$MAIN/secure/search/${enc(name)}?limit=20", timeout = 8L).text)
            }.getOrNull()?.results.orEmpty()
            val same = hits.filter { it.tmdbId == tmdbId && it.id != null }
            // Ayni TMDB kimligi bir dizinin "ozel bolumler" kaydinda da olabiliyor
            // (Frieren: iki kayit): en cok bolumu olan asil kayit.
            val hit = if (isSeries) {
                same.filter { it.type != "movie" }.maxByOrNull { it.episodeCount ?: 0 }
            } else {
                same.firstOrNull { it.type == "movie" } ?: same.firstOrNull { (it.seasonCount ?: 0) == 0 }
            } ?: same.firstOrNull()
            if (hit?.id != null) {
                idCache[key] = hit.id
                return hit.id
            }
        }
        return null
    }

    private suspend fun title(id: Int): TitleBody? = runCatching {
        tryParseJson<TitlePage>(app.get("$MAIN/secure/titles/$id?titleId=$id", headers = apiHeaders, timeout = 8L).text)
    }.getOrNull()?.title

    /**
     * AnimeciX'in sezon basina GERCEK bolum sayisi. Basliktaki `episode_count` bazen bir fazla
     * (JJK 1. sezon: 25 yaziyor, 24 bolum var); sayim video listesinden yapiliyor. Yalniz cok
     * sezonlu ve TMDB'den farkli bolunmus yapimlarda cagriliyor, o listeler kucuk.
     */
    private suspend fun seasonSizes(id: Int, seasons: List<Int>): List<Pair<Int, Int>> {
        seasonCache[id]?.let { return it }
        val sizes = seasons.map { s ->
            val videos = runCatching {
                tryParseJson<VideoPage>(
                    app.get("$MAIN/secure/related-videos?episode=1&season=$s&videoId=0&titleId=$id", timeout = 10L).text,
                )
            }.getOrNull()?.videos.orEmpty()
            s to (videos.filter { it.seasonNum == s }.mapNotNull { it.episodeNum }.maxOrNull() ?: 0)
        }
        if (sizes.all { it.second > 0 }) seasonCache[id] = sizes
        return sizes
    }

    /**
     * TMDB'deki (sezon, bolum) icin AnimeciX'te denenecek (sezon, bolum) adaylari, olasi
     * olandan baslayarak.
     */
    private suspend fun candidates(
        id: Int,
        body: TitleBody,
        season: Int,
        episode: Int,
        tmdbSeasons: Map<Int, Int>,
    ): List<Pair<Int, Int>> {
        val own = body.seasons.mapNotNull { s -> s.number?.takeIf { it > 0 }?.let { it to (s.episodeCount ?: 0) } }
            .sortedBy { it.first }
        val tmdbOrder = tmdbSeasons.filterKeys { it > 0 }.toSortedMap()
        // One Piece: TMDB'nin 4. sezonu (39 bolum) 92. bolumle basliyor, numara zaten mutlak.
        val seasonSize = tmdbOrder[season] ?: 0
        val absolute = if (seasonSize in 1 until episode) episode
        else tmdbOrder.filterKeys { it < season }.values.sum() + episode
        val out = LinkedHashSet<Pair<Int, Int>>()
        when {
            own.size <= 1 -> out += (own.firstOrNull()?.first ?: 1) to absolute
            // Ayni bolunmus: numaralar birebir.
            own.size == tmdbOrder.size && own.map { it.second } == tmdbOrder.values.toList() -> out += season to episode
            else -> {
                var left = absolute
                for ((number, size) in seasonSizes(id, own.map { it.first })) {
                    if (size <= 0) break
                    if (left <= size) {
                        out += number to left
                        break
                    }
                    left -= size
                }
            }
        }
        // Emniyet: sitenin kendi sezonlari TMDB ile ayni numaralanmis olabilir, ya da her
        // seyi tek sezona dizmistir.
        out += season to episode
        if (absolute != episode) out += 1 to absolute
        return out.toList()
    }

    /** `best-video` -> tau-video embed; 500 = sitede o bolum yok. */
    private suspend fun resolveEmbed(url: String): String? = runCatching {
        val response = app.get(url, referer = REFERER, timeout = 10L)
        if (!response.isSuccessful) return null
        response.url.takeIf { it.startsWith("http") && !it.contains("animecix.tv/secure/best-video") }
    }.getOrNull()

    private suspend fun emitEmbed(
        embed: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Int {
        if (!embed.contains("tau-video")) {
            // Baska bir yukleme sitesi (sibnet, ok.ru...): CloudStream'in hazir cozuculeri.
            var count = 0
            loadExtractor(embed, REFERER, subtitleCallback) { count++; callback(it) }
            return count
        }
        // Gomulu eski cozucu anahtari "?vid=" kuyruguyla birlikte aliyordu.
        val key = embed.substringAfter("/embed/").substringBefore("?").substringBefore("/")
        val urls = runCatching {
            tryParseJson<TauVideo>(app.get("$TAU/api/video/$key", referer = REFERER, timeout = 8L).text)
        }.getOrNull()?.urls.orEmpty()
        var count = 0
        for (video in urls) {
            val link = video.url ?: continue
            val quality = getQualityFromName(video.label).takeIf { it != Qualities.Unknown.value } ?: Qualities.P720.value
            callback(
                newExtractorLink(
                    source = NAME,
                    name = "$NAME • Türkçe Altyazı ${video.label.orEmpty()}".trim(),
                    url = link,
                    type = ExtractorLinkType.VIDEO,
                ) {
                    this.quality = quality
                    this.referer = REFERER
                }
            )
            count++
        }
        return count
    }

    /**
     * @param tmdbSeasons TMDB sezon numarasi -> bolum sayisi (0. sezon dahil olabilir, kullanilmaz).
     * @return verilen link sayisi.
     */
    suspend fun links(
        tmdbId: Int,
        names: List<String>,
        season: Int?,
        episode: Int?,
        tmdbSeasons: Map<Int, Int>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Int = try {
        val isSeries = season != null || episode != null
        val id = findId(tmdbId, names, isSeries)
        if (id == null) 0 else if (isSeries) {
            val body = title(id)
            var total = 0
            if (body != null) {
                for ((s, e) in candidates(id, body, season ?: 1, episode ?: 1, tmdbSeasons)) {
                    val embed = resolveEmbed("$MAIN/secure/best-video?titleId=$id&episode=$e&season=$s") ?: continue
                    total = emitEmbed(embed, subtitleCallback, callback)
                    if (total > 0) break
                }
            }
            total
        } else {
            val videos = title(id)?.videos.orEmpty().filter { !it.url.isNullOrBlank() }
            var total = 0
            // Turkce yuklemeler once; ilk calisan yeter.
            for (video in videos.sortedBy { if (it.language == "tr") 0 else 1 }.take(3)) {
                val url = video.url!!
                val embed = if (url.startsWith("http")) url else resolveEmbed("$MAIN/${url.trimStart('/')}") ?: continue
                total = emitEmbed(embed, subtitleCallback, callback)
                if (total > 0) break
            }
            total
        }
    } catch (error: Throwable) {
        logError(Exception("AnimeciX dogrudan yol: ${error.message}", error))
        0
    }
}
