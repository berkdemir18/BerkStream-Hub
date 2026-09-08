# Google TV Streamer kurulumu

## CloudStream içinde

1. BerkStream Hub depo adresini CloudStream'e ekle.
2. Yalnızca `BerkStream` eklentisini yükle. Birleşik film/dizi kaynak motoru paketin
   içindedir. Canlı TV rafı için ayrıca `plt-tv` yüklenirse kanal listesi eklenir.
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
