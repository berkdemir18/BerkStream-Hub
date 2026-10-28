package com.berkstream.extra

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject

/**
 * RapidVid'in 2026-10 sonu sayfa duzeni (FullHDFilmizlesene'nin oynaticisi).
 *
 * Sayfada artik `jwSetup.sources` yok; her sey `window._p8='...'` icinde. Cozumu
 * upstream'in eski `decodeSecret`'i ile ayni (ters cevir, base64, "K9L" kaydirmasi,
 * base64) ama icinden JSON cikiyor: `cm` H.264 HLS ana listesi (TV'nin cozebildigi),
 * `tm` AV1 surumu, `ct` altyazilar. Upstream bu sayfada hic link vermiyordu
 * (Cehennemde Randevu: "bulundu ama acilmadi"). Sitenin JS'i calistirilmiyor.
 */
object RapidVidP8 {
    private const val TAG = "BerkStreamRapid"
    private val payload = Regex("""window\._p8\s*=\s*['"]([^'"]+)['"]""")

    private fun atob(s: String): String {
        val padded = s.trim().let { it + "=".repeat((4 - it.length % 4) % 4) }
        return String(Base64.decode(padded, Base64.DEFAULT), Charsets.ISO_8859_1)
    }

    fun decode(encoded: String): JSONObject {
        val shifted = atob(encoded.reversed())
        val key = "K9L"
        val plain = buildString(shifted.length) {
            shifted.forEachIndexed { i, ch -> append((ch.code - (key[i % 3].code % 5 + 1)).toChar()) }
        }
        return JSONObject(String(atob(plain).toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8))
    }

    /** @return false: sayfa bu duzende degil, cagiran eski yolu denesin. */
    suspend fun extract(
        html: String,
        pageUrl: String,
        source: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val encoded = payload.find(html)?.groupValues?.get(1) ?: return false
        val data = runCatching { decode(encoded) }
            .onFailure { Log.e(TAG, "_p8 cozulemedi: $pageUrl", it) }
            .getOrNull() ?: return false
        val video = data.optString("cm").takeIf { it.startsWith("http") } ?: run {
            Log.w(TAG, "_p8 icinde H.264 adresi yok: ${data.keys().asSequence().toList()}")
            return false
        }
        val origin = Regex("""^https?://[^/]+""").find(pageUrl)?.value ?: "https://rapidvid.org"

        data.optJSONArray("ct")?.let { tracks ->
            for (i in 0 until tracks.length()) {
                val track = tracks.optJSONObject(i) ?: continue
                val file = track.optString("file").takeIf { it.startsWith("http") } ?: continue
                val label = track.optString("label").trim().ifBlank { "Altyazı" }
                subtitleCallback(newSubtitleFile(label, file))
            }
        }
        callback(
            newExtractorLink(source, source, video, ExtractorLinkType.M3U8) {
                this.referer = "$origin/"
                this.headers = mapOf("Origin" to origin)
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
