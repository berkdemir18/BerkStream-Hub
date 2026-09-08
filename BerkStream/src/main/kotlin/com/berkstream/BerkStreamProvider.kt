package com.berkstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

class BerkStreamProvider : TmdbProvider() {
    override var name = "BerkStream"
    override val apiName = "BerkStream"
    override var lang = "tr"
    override val useMetaLoadResponse = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = listOf(
        MainPageData("🎬 Vizyondaki Filmler", "this-week", false),
        MainPageData("🎟️ Geçen Hafta Vizyona Girenler", "last-week", false),
        MainPageData("🎥 Vizyondaki Diğer Filmler", "in-theaters", false),
    )

    private val boxOfficeUrl = "https://boxofficeturkiye.com/seanslar"
    private val aliases = ConcurrentHashMap<Int, Set<String>>()

    private val providerPriority = listOf(
        "plt-stream",
        "Dizilla",
        "FilmMakinesi",
        "FullHDFilm",
        "FullHDFilmizlesene",
        "HDFilmCehennemi",
        "JetFilmizle",
        "SinemaCX",
        "SetFilmIzle",
        "WebteIzle",
    )

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

    override suspend fun getMainPage(page: Int, request: MainPageRequest) =
        if (page > 1) {
            newHomePageResponse(request, emptyList<MovieSearchResponse>(), false)
        } else {
            val sectionNeedle = when (request.data) {
                "this-week" -> "bu hafta vizyona giren filmler"
                "last-week" -> "gectigimiz hafta vizyona giren filmler"
                else -> "vizyondaki diger filmler"
            }
            val section = app.get(boxOfficeUrl).document
                .select(".c-section__body")
                .firstOrNull { normalize(it.selectFirst("h3")?.text().orEmpty()).contains(normalize(sectionNeedle)) }

            val movies = section
                ?.select(".c-sessions__grid > a[href^=/film/]")
                ?.take(24)
                ?.amap { card ->
                    val title = card.selectFirst("h4")?.text()?.trim().orEmpty()
                    if (title.isBlank()) return@amap null

                    val results = super.search(title, 1)
                        ?.items
                        ?.filterIsInstance<MovieSearchResponse>()
                        .orEmpty()
                    val exact = results.firstOrNull { normalize(it.name) == normalize(title) }
                        ?: results.firstOrNull()

                    exact?.apply {
                        id?.let { movieId -> aliases[movieId] = setOf(title, name) }
                        card.selectFirst("img")?.attr("src")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { posterUrl = it }
                    }
                }
                ?.filterNotNull()
                .orEmpty()

            newHomePageResponse(request, movies, false)
        }

    private val validApis
        get() = apis
            .filter {
                it.name != name &&
                    it.lang == "tr" &&
                    it.providerType != ProviderType.MetaProvider &&
                    TvType.Movie in it.supportedTypes
            }
            .sortedBy { api ->
                providerPriority.indexOfFirst { it.equals(api.name, ignoreCase = true) }
                    .let { if (it == -1) Int.MAX_VALUE else it }
            }
            .take(16)

    override suspend fun load(url: String): LoadResponse? {
        val base = super.load(url) ?: return null
        if (base !is MovieLoadResponse) {
            throw ErrorLoadingException("BerkStream şu anda yalnız filmleri destekliyor")
        }

        val tmdbId = Regex("themoviedb\\.org/movie/(\\d+)")
            .find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val queryNames = buildSet {
            add(base.name)
            tmdbId?.let { aliases[it] }?.let(::addAll)
        }.filter { it.isNotBlank() }
        val normalizedNames = queryNames.map(::normalize).toSet()

        val matches = validApis.amap { api ->
            try {
                val hit = queryNames.firstNotNullOfOrNull { query ->
                    api.search(query)?.firstOrNull { result ->
                        normalize(result.name) in normalizedNames &&
                            (result !is MovieSearchResponse ||
                                result.year == null || base.year == null || result.year == base.year)
                    }
                } ?: return@amap null

                (api.load(hit.url) as? MovieLoadResponse)?.let { response ->
                    response.apiName to response.dataUrl
                }
            } catch (error: Exception) {
                logError(error)
                null
            }
        }.filterNotNull()

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
                    providerData,
                    isCasting,
                    subtitleCallback,
                    callback,
                )
            } catch (error: Exception) {
                logError(error)
            }
        }
        return true
    }
}
