// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (Watch2Movies).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.watch2movies.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.berkstream.vendor.feroxx.watch2movies.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty


@Suppress("unused")
data class Sources(
    @JsonProperty("type") val type: String,
    @JsonProperty("link") val link: String,
    @JsonProperty("sources") val sources: List<String?>,
    @JsonProperty("tracks") val tracks: List<String?>,
    @JsonProperty("title") val title: String
)