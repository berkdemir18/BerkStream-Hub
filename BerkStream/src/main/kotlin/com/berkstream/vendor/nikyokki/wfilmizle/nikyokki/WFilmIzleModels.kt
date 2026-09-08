// BerkStream tarafindan Nikyokki Turkish Providers deposundan derlendi (WFilmİzle).
// Kaynak paket: com.nikyokki -> com.berkstream.vendor.nikyokki.wfilmizle.nikyokki
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.nikyokki.wfilmizle.nikyokki

import com.fasterxml.jackson.annotation.JsonProperty

data class IframeResponse(
    @JsonProperty("hls") val hls: Boolean,
    @JsonProperty("videoImage") val videoImage: String,
    @JsonProperty("videoSource") val videoSource: String,
    @JsonProperty("securedLink") val securedLink: String,
    @JsonProperty("downloadLinks") val downloadLinks: List<String>,
    @JsonProperty("attachmentLinks") val attachmentLinks: List<String>,
    @JsonProperty("ck") val ck: String
)