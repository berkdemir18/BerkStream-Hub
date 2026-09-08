// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (DiziPalOriginal).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.dizipaloriginal.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.berkstream.vendor.feroxx.dizipaloriginal.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty


data class DizipalSearchData(
    @JsonProperty("success") val success: Boolean?,
    @JsonProperty("results") val results: List<DizipalSearchResult>?
)

data class DizipalSearchResult(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("year") val year: Int?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("poster") val poster: String?,
    @JsonProperty("url") val url: String?,
    @JsonProperty("rating") val rating: String?
)