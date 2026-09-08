# Google TV Streamer kurulumu

## CloudStream içinde

1. BerkStream depo adresini CloudStream'e ekle:
   `https://raw.githubusercontent.com/berkdemir18/BerkStream-Hub/builds/repo.json`
2. Depoda tek paket var: `BerkStream`. Onu yükle — 60+ Türkçe kaynak, canlı TV ve
   birleşik film/dizi kaynak motoru paketin içindedir, ayrıca bir şey kurmana gerek yok.
3. CloudStream ana ekranındaki provider seçicisinden `BerkStream`i seç.
4. İlk satır `🎬 VİZYON • BU HAFTA` olur. Devamında popüler, film, dizi,
   platform seçkileri, canlı TV ve günlük sürpriz rafları görünür.

## Google TV ana ekranında

CloudStream'in güncel TV sürümü, seçili provider'ın **ilk ana sayfa satırını**
Android TV preview channel olarak yayınlar. Bu yüzden BerkStream'de vizyon satırı
bilerek ilk sıradadır.

Kurulumdan sonra CloudStream'i bir kez açıp BerkStream ana sayfasının yüklenmesini
bekle. Google TV ana ekranında kanal görünmüyorsa ana ekran ayarlarından CloudStream
kanalını etkinleştir ve cihazı yeniden başlat.

`Devam Et` satırı eklentinin değil CloudStream çekirdeğinin izleme geçmişinden gelir.
Bir filmi CloudStream'in dahili oynatıcısıyla başlatıp bir süre izledikten sonra çık;
uygulama desteklenen Google TV/Android TV launcher'larında ilerlemeyi sistemin
`Watch Next` satırına kendisi gönderir.

> Google TV launcher sürümüne göre üçüncü taraf preview channel satırları gizlenebilir.
> BerkStream uygulama içindeki vizyon satırını her durumda sağlar; TV ana ekranındaki
> görünürlük launcher'ın Android TV kanal desteğine bağlıdır.
