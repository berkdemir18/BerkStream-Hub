// BerkStream tarafindan Feroxx Turkish Providers deposundan derlendi (InatBox).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.feroxx.inatbox.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.feroxx.inatbox.keyiflerolsun

data class ChContent(
    val chName : String,
    val chUrl : String,
    val chImg : String,
    val chHeaders : String,
    val chReg : String,
    val chType: String
)