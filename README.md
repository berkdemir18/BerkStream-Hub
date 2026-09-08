# BerkStream

BerkStream artık **tek eklenti**. PLT Stream'in yaptığı gibi bütün kaynaklar
tek `.cs3` dosyasının içine gömülüdür: kurulumda tek satır eklersin, 60+ Türkçe
kaynak birden açılır. Tek tek eklenti kurmak, tek tek güncellemek yok.

Pakette ayrıca `BerkStream` adlı özel bir ana provider bulunur. Ana sayfası vizyon,
günün popülerleri, en çok izlenen filmler, gündemdeki diziler, Netflix, Prime Video,
Disney+, HBO Max, tabii, canlı TV ve günlük sürpriz seçki raflarından oluşur.
Platform rafları Türkiye kataloğuna göre film ve dizileri ayrı ayrı toplayıp
dönüşümlü gösterir. Film açıldığında kurulu Türkçe provider'ları tarar;
paketin içine gömülü birleşik kaynak motoru ilk tercihidir. Film ve dizilerde
bulunan farklı sunucular ile altyazılar CloudStream oynatıcısının kaynak ve altyazı
menülerinde birlikte seçilebilir.

Ana sayfa yanıtları önbelleğe alınır; aynı ekran yenilendiğinde uzak sayfalar tekrar
tekrar çağrılmaz. İki harften sonra çalışan hızlı arama önerileri de etkindir.

## Kurulum

CloudStream → Ayarlar → Eklentiler → Depo ekle:

```text
https://raw.githubusercontent.com/berkdemir18/BerkStream-Hub/builds/repo.json
```

Depoda tek paket görürsün: **BerkStream**. Onu kur, bitti.

Tek tek kurmayı tercih edersen eski katalog da yayında kalıyor:

```text
https://raw.githubusercontent.com/berkdemir18/BerkStream-Hub/builds/repo-full.json
```

## Tek eklenti nasıl toplanıyor?

`scripts/vendor-sources.mjs`, upstream depolardaki her sağlayıcı modülünün Kotlin
kaynağını çeker ve `BerkStream/src/main/kotlin/com/berkstream/vendor/` altına
kopyalar. Her modül kendine özel bir pakete taşınır
(`com.keyiflerolsun` → `com.berkstream.vendor.feroxx.dizipal.keyiflerolsun`), böylece
farklı depolardaki aynı isimli sınıflar (`DiziPal`, `SearchItem`, `IptvPlaylistParser`…)
birbirini ezmez. Alt eklentilerin `@CloudstreamPlugin` işareti kaldırılır — bir `.cs3`
içinde tek giriş noktası olabilir — ve hepsi üretilen `VendoredSources.kt` listesi
üzerinden `BerkStreamPlugin` tarafından kaydedilir.

Her kaynak ayrı ayrı `runCatching` içinde yüklenir: biri patlarsa diğerleri açılmaya
devam eder. Kapalı kaynak olan PLT Stream motoru derlenemediği için CI, PLT'nin
yayımladığı `.cs3`in dex'ini pakete ekler ve motor yansımayla ayağa kaldırılır.

Kaynak listesi ve neyin neden atlandığı `vendor-report.json` dosyasında.

Yerelde yeniden toplamak için:

```powershell
npm run vendor
```

## Kaynaklar

- `recloudstream/extensions`: resmî, kamuya açık provider'lar
- `pltmustafa/plt-stream`: PLT Stream, PLT TV, Party ve senkron araçları
- `feroxx/Kekik-cloudstream`: birincil Türkçe provider kaynağı
- `nikyokki/nik-cloudstream`: birincil kaynakta bulunmayan ek Türkçe provider'lar

Yalnız manifestinde `status: 1` olan eklentiler alınır. Aynı eklenti birden çok
kaynakta varsa resmî kaynak, ardından PLT Stream, Feroxx ve Nikyokki tercih edilir.
Eklentilerin telif ve lisans koşulları kendi kaynak depolarına aittir; gömülen
kaynak kodu değiştirilmeden, kaynağı ve orijinal paketi belirtilerek taşınır.

## Yerelde güncelleme ve doğrulama

Node.js 22 veya daha yenisiyle:

```powershell
npm run check
$env:BERKSTREAM_REPOSITORY = "GitHubKullaniciAdin/BerkStream-Hub"
npm run verify
```

`verify` komutu her `.cs3`/`.jar` dosyasını indirir, HTTP erişimini ve manifestte
varsa SHA-256 değerini denetler, sonra `plugins.json` (tek eklenti),
`plugins-all.json` (tekil paketler), `repo.json`, `repo-full.json` ve
`catalog-report.json` dosyalarını üretir.

## GitHub'a koyma

1. Bu klasörü `BerkStream-Hub` adlı bir GitHub deposuna yükle.
2. Actions sekmesinde `Update plugin catalog` iş akışını bir kez çalıştır.
3. CloudStream'e aşağıdaki depo adresini ekle:

```text
https://raw.githubusercontent.com/berkdemir18/BerkStream-Hub/builds/repo.json
```

İş akışı kataloğu her pazartesi yeniler ve bütünlük kontrolünden geçmeyen
bir paketi yayımlamaz.

Google TV Streamer kurulumu ve ana ekran davranışı için [`TV-KURULUM.md`](TV-KURULUM.md)
dosyasına bak.

## Doğrulama sınırı

Dosya doğrulaması; paketin erişilebilir, eksiksiz ve bildirilen hash ile uyumlu
olduğunu kanıtlar. Kaynak sitelerin arama, bölüm ve oynatma akışlarının gerçekten
çalışması Android üzerinde CloudStream ile ayrıca denenmelidir. DRM, üyelik veya
ödeme duvarı aşan değişiklikler bu projenin kapsamı değildir.
