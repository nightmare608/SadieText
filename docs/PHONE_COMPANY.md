# Telefon Şirketim — SadieText 0.3.0

Sohbetler ekranının sağ üstündeki **Sağ menü → Oyunlar → Telefon Şirketim** yolundan açılır.
Firebase sürümü ve çevrimdışı önizleme aynı yerel oyun motorunu kullanır. Sunucuda yeni
koleksiyon, izin veya Firestore kuralı gerekmez. Sohbet/veri şifreleme katmanı değiştirilmez.

## Oynanış

- ₺500.000 oyun sermayesiyle şirket kurma; şirket ve model isimleri.
- Aranabilir, üretici filtreli geniş mobil/tarihî işlemci kataloğu.
- RAM, depolama, ekran, kamera, pil, kasa, soğutma, renk, yazılım desteği ve hedef kitle.
- Parçalar değiştikçe telefon çizimi, maliyet, performans/kamera/pil/tasarım puanları.
- Onaylı üretim/lansman; başlangıç geliştirme maliyeti ve üretim bütçesi.
- Stok, ek üretim, değiştirilebilir fiyat, satışı durdurma/açma, iade takibi.
- Haftalık satış; fiyat esnekliği, marka itibarı, reklam, ekibin etkisi, model yaşlanması,
  aynı segmentte model rekabeti, pazar olayları ve soğutma/kalite kaynaklı arızalar.
- Oyuncu, kamera, fiyat/performans ve premium müşteri kitleleri.
- Ar-Ge kilitleri, fabrika, kalite kontrol, satış ekibi ve haftalık reklam kampanyaları.
- Simüle müşteri yorumları, beğenilme yüzdesi, hedefler ve haftalık grafik.
- Nakit, stok değeri, özkaynak, borç, ciro, kâr/zarar, nakit hareketleri.
- ₺100.000 kredi dilimleri; ₺400.000 limit; haftalık %1 oyun faizi; anapara ödeme.
- Çevrimdışı, tek oyunculu ve ücretsiz. Hafta ilerletilmedikçe satış/gider gerçekleşmez.

## Muhasebe

Üretim stok varlığıdır: nakit düşer, kâr henüz değişmez. Satılan ürünün maliyeti,
iade edilen ürünün maliyeti ve iade işlem masrafı satış gerçekleşince giderleşir.
İade ürünü yeniden satılabilir stoğa geri dönmez. Geliştirme/Ar-Ge/fabrika yatırımları
oyun ölçeğinde doğrudan giderleşir; amortisman modellenmez. Kredi anaparası kâr değildir.
Hafta raporu aynı haftada yapılan geliştirme/yatırımları da kapsar.
Üretim kapasitesi **tek üretim partisi** sınırıdır. Her modelin üretim birim maliyeti
lansmanda sabitlenir; fabrika iyileştirmesi yeni model maliyetlerine uygulanır.

İlk parti için 20–50 adet önerilir. Aşırı pahalı parçalar, yüksek fiyat, reklam gideri,
kontrolsüz üretim veya fazla kredi zarar ettirebilir. Nakit eksiye düşebilir; oyuncu stok
satışı ve kredilerle toparlanabilir veya onaylı yeni şirket kurabilir. Gerçek iflas/hukuk
süreçleri modellenmez.

## Kayıt ve sınırlar

Her işlem, arka plan IO iş parçacığında Android AtomicFile ile atomik kaydedildikten
sonra ekrana uygulanır. Yazma hatasında işlem geri alınır. Okuma hatasında mevcut kayıt
ezilmez. Firebase UID'sinin SHA-256 özetiyle ayrı dosya seçilir; demo ayrı kaydedilir.
Kayıtlar uygulamanın özel dosya alanındadır. Mesajları okumaz/yüklemez. Bulut kaydı veya
cihazlar arası senkronizasyon yoktur; uygulama silinmesi/veri temizleme kaydı kaybettirir.

100 modele, 104 haftalık rapora ve 350 nakit hareketine kadar tutulur. Uzun dönemde eski
ayrıntılı hareketler düşer; mevcut nakit/stok/borç ve modelin toplam satış/iadesi korunur.
Gerçek zamanlı rakip şirketler veya çevrimiçi sıralama yoktur. Aynı segmentte kendi
modellerinin rekabeti ve pazar hareketleri talebe yansıtılır.

## İşlemci verilerinin kapsamı

Katalog dünyanın bütün CPU/SoC modellerinin eksiksiz arşivi değildir. Mobil telefon
şirketi teması için Qualcomm, MediaTek, Samsung, Apple, Google, HiSilicon, UNISOC ve
tarihî NVIDIA/Intel/TI ailelerinden isimler içerir. Gerçek ürün isimleri ile **kurgusal
oyun puanları, fiyatları, teknoloji kilitleri** birbirinden ayrıdır. Puanlar benchmark
veya üreticilerin karşılaştırma iddiası değildir. Katalog sırası tarih/performance
sıralaması olarak kullanılmamalıdır. Apple/Tensor/Kirin gibi normalde başka üreticilere
serbestçe satılmayan platformları seçebilmek de oyun kurgusudur. Ticari iş birliği yoktur.

Üretici katalogları / adlandırma referansları:
- https://www.qualcomm.com/smartphones/products
- https://www.mediatek.com/products/smartphones/dimensity-5g
- https://www.mediatek.com/products/smartphones/mediatek-helio
- https://semiconductor.samsung.com/processor/mobile-processor/
- https://semiconductor.samsung.com/processor/showcase/smartphone/
- https://www.apple.com/newsroom/2026/09/apple-debuts-iphone-18-pro-and-iphone-18-pro-max/

## Sohbet akıcılığı

Mesajlar ve sohbetler sabit anahtarlı LazyColumn'da animateItem kullanır. Yeni mesajda
sonda okuyan kullanıcı veya kendi mesajını gönderen kullanıcı yumuşakça alta kayar;
geçmişi okuyan kullanıcı zorla aşağı çekilmez. Önceki mesajları yüklemek / teslim
makbuzlarını yenilemek yeni mesaj kaydırması başlatmaz. İlk açılışta son mesaj gösterilir;
klavye alanı değişiminde sonda olan kullanıcı sonda tutulur. Son mesaja dön düğmesi,
yuvarlatılmış yönlü balonlar ve boyutu uyarlanan yazma alanı vardır.
