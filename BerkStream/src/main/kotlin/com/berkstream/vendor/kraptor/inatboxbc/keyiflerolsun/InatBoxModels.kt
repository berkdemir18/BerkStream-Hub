// BerkStream tarafindan Kraptor Turkish Providers deposundan derlendi (inatbox-bc).
// Kaynak paket: com.keyiflerolsun -> com.berkstream.vendor.kraptor.inatboxbc.keyiflerolsun
// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.
package com.berkstream.vendor.kraptor.inatboxbc.keyiflerolsun

data class ChContent(
    val chName : String,
    val chUrl : String,
    val chImg : String,
    val chHeaders : String,
    val chReg : String,
    val chType: String
)