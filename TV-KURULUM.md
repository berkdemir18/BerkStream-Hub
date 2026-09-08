# Google TV Streamer kurulumu

## CloudStream içinde

1. BerkStream Hub depo adresini CloudStream'e ekle.
2. `BerkStream` ile birlikte kullanmak istediğin Türkçe kaynakları yükle. En az
   `plt-stream` kurulu olsun; BerkStream kaynak ararken onu ilk sırada dener.
3. CloudStream ana ekranındaki provider seçicisinden `BerkStream`i seç.
4. İlk satır `🎬 Vizyondaki Filmler` olur. Sonraki satırlarda geçen hafta vizyona
   girenler ve vizyondaki diğer filmler görünür.

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
