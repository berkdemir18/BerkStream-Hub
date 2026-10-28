// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (DiziPod).
// Kaynak paket: com.patr0n -> com.berkstream.vendor.feroxx.dizipod.patr0n
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.dizipod.patr0n

import com.fasterxml.jackson.annotation.JsonProperty

data class PlayerResponse(
    @JsonProperty("success") val success: Boolean = false,
    @JsonProperty("data")    val data   : String   = ""
)

data class SearchResult(
    @JsonProperty("title") val title : String = "",
    @JsonProperty("url")   val url   : String = "",
    @JsonProperty("img")   val img   : String = ""
)
