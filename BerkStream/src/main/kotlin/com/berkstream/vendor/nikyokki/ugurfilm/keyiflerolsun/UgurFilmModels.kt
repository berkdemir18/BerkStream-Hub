// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (UgurFilm).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.nikyokki.ugurfilm.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.berkstream.vendor.nikyokki.ugurfilm.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty


data class AjaxSource(
    @JsonProperty("status")      val status: String,
    @JsonProperty("iframe")      val iframe: String,
    @JsonProperty("alternative") val alternative: String,
)