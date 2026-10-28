// BerkStream tarafindan NeO Turkish Providers deposundan derlendi (UgurFilm).
// Kaynak paket: com.neowatch -> com.berkstream.vendor.neowatch.ugurfilm.neowatch
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.neowatch.ugurfilm.neowatch

import com.fasterxml.jackson.annotation.JsonProperty


data class AjaxSource(
    @JsonProperty("status")      val status: String,
    @JsonProperty("iframe")      val iframe: String,
    @JsonProperty("alternative") val alternative: String,
)