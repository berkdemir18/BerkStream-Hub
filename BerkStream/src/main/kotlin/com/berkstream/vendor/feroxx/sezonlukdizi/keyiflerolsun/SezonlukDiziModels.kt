// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (SezonlukDizi).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.sezonlukdizi.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.berkstream.vendor.feroxx.sezonlukdizi.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty


data class Kaynak(
    @JsonProperty("status") val status: String,
    @JsonProperty("data") val data: List<Veri>,
)

data class Veri(
    @JsonProperty("baslik") val baslik: String,
    @JsonProperty("id") val id: Int,
    @JsonProperty("kalite") val kalite: Int,
)

data class AspData(
    val alternatif : String,
    val embed : String
)