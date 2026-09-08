// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (FilmModu).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.filmmodu.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.berkstream.vendor.feroxx.filmmodu.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty


data class GetSource(
    @JsonProperty("subtitle") val subtitle: String?       = null,
    @JsonProperty("sources")  val sources: List<Sources>? = arrayListOf()
)

data class Sources(
    @JsonProperty("src")   val src: String,
    @JsonProperty("label") val label: String,
)