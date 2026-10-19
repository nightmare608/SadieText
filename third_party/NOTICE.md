# Üçüncü taraf bileşenler

Uygulamanın birleşik dağıtımı AGPL-3.0-only lisansıyla hazırlanır. Aşama 1'de
MIT altında üretilen kodun bildirimi LICENSE-stage1-MIT.txt dosyasında korunur.
Signal entegrasyonu Signal Messenger tarafından desteklenen bir ürün değildir.

## Signal libsignal 0.105.1

Resmi kaynak: https://github.com/signalapp/libsignal/tree/v0.105.1
Maven: https://build-artifacts.signal.org/libraries/maven/
Lisans: AGPLv3; tam metin proje kökündeki LICENSE dosyasında.
Kesin sürümün upstream kaynak arşivi bu klasöre eklenmiştir. Arşiv kütüphanenin
kaynaklarını, Cargo.lock ve upstream derleme betiklerini içerir. Kriptografik
protokol kodu uygulama içinde yeniden yazılmadı.

AGPL kapsamındaki APK yayımlanırken uygulamanın karşılık gelen kaynak kodunu,
libsignal'ın tam karşılık gelen kaynaklarını, değişiklikleri, derleme ve kurulum
bilgilerini aynı sürümde erişilebilir tutun. Sadece depo ana sayfasına bağlantı
vermekle yetinmeyin. Upstream Rust/Java bağımlılıklarının kilitli sürümlerini,
acknowledgments klasöründeki lisansları ve gerektiğinde kaynaklarını da koruyun.
Uygulama içindeki kaynak bildiriminin yanına gerçek GitHub deponuzun/sürümünüzün
bağlantısını yayımdan önce ekleyin.

## Diğer bileşenler

AndroidX/Compose, Kotlin, Coroutines ve Firebase SDK'larının lisans bildirimleri
kendi Maven paketlerinde bulunur. Bu paketlerin lisansları ve yükümlülükleri
saklıdır. Firebase hizmet şartları, açık kaynak SDK lisanslarından ayrıdır.
Test için kullanılan Firebase CLI/npm bağımlılıkları APK içinde dağıtılmaz.
