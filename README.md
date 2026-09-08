# BerkStream Hub

BerkStream Hub, birden fazla güncel CloudStream deposundaki aktif eklentileri
tek katalogda toplar. Aynı isimli eklentiler tekilleştirilir; öncelik sırası
`sources.json` içindeki `priority` alanıyla belirlenir.

Pakette ayrıca `BerkStream` adlı özel bir ana provider bulunur. Box Office
Türkiye'nin anahtarsız, herkese açık seans sayfasından bu hafta vizyona girenleri,
geçen haftanın yenilerini ve vizyondaki diğer filmleri gösterir. Bir film
açıldığında kurulu Türkçe provider'ları tarar; `plt-stream` ilk tercihidir.

## Neden tek `.cs3` değil?

CloudStream'in doğal modeli her provider'ı ayrı eklenti olarak paketlemektir.
Bu proje hepsini **tek depo adresinden** sunar fakat modülleri ayrı tutar. Böylece
bir sitenin bozulması diğer provider'ları devre dışı bırakmaz ve her eklenti
bağımsız güncellenebilir.

## Kaynaklar

- `recloudstream/extensions`: resmî, kamuya açık provider'lar
- `pltmustafa/plt-stream`: PLT Stream, PLT TV, Party ve senkron araçları
- `feroxx/Kekik-cloudstream`: birincil Türkçe provider kaynağı
- `nikyokki/nik-cloudstream`: birincil kaynakta bulunmayan ek Türkçe provider'lar

Yalnız manifestinde `status: 1` olan eklentiler alınır. Aynı eklenti birden çok
kaynakta varsa resmî kaynak, ardından PLT Stream, Feroxx ve Nikyokki tercih edilir.
Eklentilerin telif ve lisans koşulları kendi kaynak depolarına aittir.

## Yerelde güncelleme ve doğrulama

Node.js 22 veya daha yenisiyle:

```powershell
npm run check
$env:BERKSTREAM_REPOSITORY = "GitHubKullaniciAdin/BerkStream-Hub"
npm run verify
```

`verify` komutu her `.cs3`/`.jar` dosyasını indirir, HTTP erişimini ve manifestte
varsa SHA-256 değerini denetler, sonra `plugins.json`, `repo.json` ve
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
