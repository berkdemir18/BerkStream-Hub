// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (JetFilmizle).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.jetfilmizle.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.jetfilmizle.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty

data class Source(
    @JsonProperty("file") val file: String,
    @JsonProperty("label") val label: String,
    @JsonProperty("type") val type: String,
)