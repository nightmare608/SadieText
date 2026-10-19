# Doğrulama raporu — SadieText / 18 Ekim 2026

Gönderilen gerçek Firebase dosyasıyla aşağıdaki temiz derleme başarılı oldu:

```
./gradlew clean assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

- Google Services kaynaklarındaki proje/uygulama kimlikleri dosyayla eşleşti.
- HAS_FIREBASE=true doğrulandı; yapılandırılmış debug APK oluşturuldu.
- 10/10 gerçek libsignal JVM entegrasyon testi geçti; rapor verification/signal-junit.xml.
- Android test APK'si derlendi. Fiziksel cihazda veya Android emülatöründe çalıştırılmadı.
- Lint derlemesi geçti; sabitlenmiş bağımlılık sürümüne ilişkin uyarı hata değildir.
- Önceki entegrasyonda 27/27 Firestore emülatör kural testi geçmişti;
  kurallar bu bağlantı güncellemesinde değiştirilmedi. Özet verification/ içinde.
- Önceki temel entegrasyonda R8 release derlemesi de geçmişti; bu bağlantı
  güncellemesinde release yeniden derlenmedi. Dağıtılan APK debug önizlemesidir.

## Canlı testin sınırı

Authentication: HTTP 400 / CONFIGURATION_NOT_FOUND.
Firestore: HTTP 403 / SERVICE_DISABLED.
Ayrıntı ve konsol adımları FIREBASE_CONNECTION.md içindedir. Bunlar tamamlanmadan
canlı kayıt, yetkili veri erişimi veya iki telefonla mesajlaşma onaylanmış değildir.
Sunucuda kullanıcı/veri oluşturulmadı, kurallar yayımlanmadı, plan değiştirilmedi.

## Teslim

SadieText-Android-Onizleme.apk: 61,388,139 bayt.
SHA-256: `cbc93e1cafa1ce6fcc15999f3ec3c606eff90a2fe9d037b21f7d2a0521d224a4`

Uygulama adı SadieText; paket kimliği tr.sakin.chat. Açılıştaki iki Kabul et
seçeneği de devam ettirir. JSON Git'ten ve kaynak ZIP'ten hariç tutulur.
Özel hesap/şifre/servis anahtarı kaynak pakete konmadı. APK, standart Firebase
istemci yapılandırmasını içerir; üretim APK'sini kendi keystore'unuzla imzalayın.

JDK 21, Gradle 8.11.1, AGP 8.10.1, Kotlin 2.2.20, SDK 35, Build Tools 35.0.0,
NDK 27.0.12077973, Firebase BoM 34.0.0, libsignal 0.105.1 kullanıldı.
Bağımsız güvenlik incelemesi ve gerçek cihaz performans/teslim testi yapılmadı.
