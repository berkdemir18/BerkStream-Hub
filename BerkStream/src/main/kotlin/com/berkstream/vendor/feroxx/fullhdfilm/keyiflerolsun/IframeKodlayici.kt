// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (FullHDFilm).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.fullhdfilm.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.berkstream.vendor.feroxx.fullhdfilm.keyiflerolsun

import android.util.Base64

class IframeKodlayici {
    companion object {
        fun tersCevir(metin: String): String {
            return metin.reversed()
        }

        fun base64Coz(encodedString: String): String {
            val decodedBytes = Base64.decode(encodedString, Base64.DEFAULT)
            return String(decodedBytes, Charsets.UTF_8)
        }

        fun iframeParse(htmlIcerik: String): String {
            val iframePattern = """<iframe[^>]+src=["']([^"']+)["'][^>]*>""".toRegex()
            val match = iframePattern.find(htmlIcerik)
            return match?.groupValues?.get(1) ?: throw IllegalArgumentException("Iframe src bulunamadı")
        }
    }

    fun iframeCoz(veri: String): String {
        var tempVeri = veri
        if (!tempVeri.startsWith("PGltZyB3aWR0aD0iMTAwJSIgaGVpZ2")) {
            tempVeri = tersCevir("BSZtFmcmlGP") + tempVeri
        }
        val iframe = base64Coz(tempVeri)
        return iframeParse(iframe)
    }
}