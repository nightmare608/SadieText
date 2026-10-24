# SadieText 0.3.0 — doğrulama

2026-10-24, Linux / JDK 21 / Android SDK 35 / Gradle 8.11.1.

- `-PofflinePreview=true assembleDebug`: başarılı.
- `testDebugUnitTest`: 31 test, 0 hata. Bunun 17'si yeni oyun motoru/katalog/kayıt/muhasebe
  testi, 14'ü mevcut mesajlaşma/Signal testidir.
- `lintDebug`: başarılı; hata yok. Bağımlılık sürüm güncellemesi uyarıları mevcut.
- Android 11 x86_64 PhoneTest emülatörü, 480×960, software GPU.
- Son teslim APK'sı kurularak 8 cihaz testi geçti: şirket kurma/tasarım/lansman/satış/yorum,
  geçmişi okurken yeni mesajda konumu koruma, sağ menüden oyuna giriş/çıkış,
  demo mesaj ekleme/silme, çevrimdışı paket ağ izni denetimi, atomik kayıt yeniden
  açma/hesap izolasyonu ve iki mevcut Android Keystore/gerçek libsignal JNI testi.
- APK imzası v2/v3 doğrulandı; native kütüphanelerin 16 KB ZIP hizalaması doğrulandı.
- Kaynakta yeni Firebase sorgusu/koleksiyonu/izni eklenmedi; oyun UID'yi yalnızca
  yerel dosya ayrımı için kullanır. Firebase yapılandırması bu çalışma alanında yoktur.
  Canlı Firebase APK'sı mevcut GitHub Actions akışı ve mevcut yapılandırmasıyla derlenir.
- Gerçek telefon FPS ölçümü veya iki gerçek telefonla canlı sohbet testi yapılmadı.
  “Her cihazda 60 FPS” ya da kusursuzluk garantisi verilmez.

Yerel teslim: `SadieText-Telefon-Sirketim.apk`, paket `tr.sakin.chat.offline`, sürüm
`0.3.0-offline-preview`, versionCode 6. Bu paket oyun ve arayüz demosudur; gerçek
mesaj göndermez. Normal `tr.sakin.chat` uygulamasından ayrı yüklenir. Debug imzalıdır.
Yerel SDK'da NDK olmadığından, `llvm-strip --strip-debug` ile yalnızca hata ayıklama
sembolleri çıkarıldı; ABI'ler ve JNI simgeleri korundu, orijinal ZIP sıkıştırma yöntemleri
korundu, zipalign ve apksigner yeniden çalıştırıldı. Bu son APK üzerinde cihaz testleri
tekrar çalıştırıldı. CI normal NDK ile aynı kaynaklardan standart APK üretir.

Görsel kontroller: docs/screenshots/phone-company.png ve phone-designer.png.
