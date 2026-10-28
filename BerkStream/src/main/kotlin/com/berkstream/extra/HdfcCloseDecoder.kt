package com.berkstream.extra

import android.util.Base64
import android.util.Log

/**
 * HDFilmCehennemi "Close" oynaticisinin video adresi cozucusu.
 *
 * 2026-10 sonunda site adresi yeni bir fonksiyonla sifrelemeye basladi:
 * parcalardan ikisi cikariliyor, biri islem sirasi, digeri anahtar
 * (embed sayfasinin hostname'i eklenerek); sonra base64 / ters cevirme /
 * Sezar kaydirma, sabit tohumlu karistirma ve XOR. Upstream cozucu bunu
 * cozemiyor ve bozuk adres veriyordu (Attack Force Z: "Baglanti bulunamadi").
 * Fonksiyon ve degisken adlari her sayfada rastgele, sabitler ve adimlar ayni
 * (uc farkli filmle dogrulandi). Sayfadaki JS calistirilmiyor; algoritma
 * burada elle yazili.
 */
object HdfcCloseDecoder {
    private val fileVar = Regex("""file\s*:\s*([A-Za-z_$][\w$]*)""")
    private val splitArg = Regex("""^["']([^"']*)["']\s*\.\s*split\s*\(\s*["']([^"']*)["']\s*\)$""")
    private val quoted = Regex(""""([^"]*)"""")

    /** @return cozulen adres; sayfa bu duzende degilse null. */
    fun decode(html: String, embedUrl: String): String? = runCatching {
        val variable = fileVar.find(html)?.groupValues?.get(1) ?: return null
        val call = Regex("""(?:var|let|const)\s+${Regex.escape(variable)}\s*=\s*[A-Za-z_$][\w$]*\s*\((.*?)\)\s*;""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.trim() ?: return null
        val parts = splitArg.find(call)?.let { m -> m.groupValues[1].split(m.groupValues[2]) }
            ?: if (call.startsWith("[")) quoted.findAll(call).map { it.groupValues[1] }.toList() else return null
        if (parts.size < 13) return null
        // URI kullanilmiyor: upstream adresi "/{rapidrame_id}" ekiyle veriyor ve
        // URI o suslu parantezde patlayip cozucuyu sessizce null'a dusuruyordu.
        val host = Regex("""^https?://([^/:?#]+)""").find(embedUrl)?.groupValues?.get(1) ?: return null
        decrypt(parts, host).takeIf { it.startsWith("http") }
            ?: null.also { Log.w(TAG, "Cozum adres vermedi: $embedUrl") }
    }.onFailure { Log.e(TAG, "Cozulemedi: $embedUrl", it) }.getOrNull()

    private const val TAG = "BerkStreamHdfc"

    // Android'in cozucusu eksik '=' dolgusunda "bad base-64" atiyor; tarayicinin
    // atob'u ve JVM'deki testler bunu tolere ettigi icin ilk surum cihazda susuyordu.
    private fun atob(s: String): String {
        val padded = s.trim().let { it + "=".repeat((4 - it.length % 4) % 4) }
        return String(Base64.decode(padded, Base64.DEFAULT), Charsets.ISO_8859_1)
    }

    fun decrypt(input: List<String>, host: String): String {
        val parts = input.toMutableList()
        val n = parts.size - 2
        val ops = parts.removeAt(8 + n % 5)
        val key = parts.removeAt(n % 7) + host
        var text = parts.joinToString("")

        var d = 0
        var a = 0
        key.forEachIndexed { i, ch ->
            val c = ch.code
            d = (d * 37 + c) % 241
            a = (a + ((c shl 1) xor i)) and 255
        }
        val xorStart = (d * 3 + a) % 256
        val xorStep = (a % 11) + 5
        var seed = ((a * 251 + d) % 65519) + 1

        for (i in ops.indices.reversed()) {
            when (val op = ops[i]) {
                '7' -> text = atob(text)
                '3' -> text = text.reversed()
                else -> {
                    val shift = (26 - ((op.code - 96) % 26)) % 26
                    text = buildString(text.length) {
                        for (ch in text) {
                            append(
                                when (ch) {
                                    in 'A'..'Z' -> 'A' + (ch - 'A' + shift) % 26
                                    in 'a'..'z' -> 'a' + (ch - 'a' + shift) % 26
                                    else -> ch
                                }
                            )
                        }
                    }
                }
            }
        }

        val chars = text.toCharArray()
        val swaps = IntArray(chars.size)
        for (i in chars.size - 1 downTo 1) {
            seed = (seed * 97 + 41) % 65519
            swaps[i] = seed % (i + 1)
        }
        for (i in 1 until chars.size) {
            val j = swaps[i]
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }

        var q = xorStart
        return buildString(chars.size) {
            for (ch in chars) {
                val c = ch.code
                q = (q * 5 + xorStep) % 256
                append((c xor q).toChar())
                q = (q + c) % 256
            }
        }
    }
}
