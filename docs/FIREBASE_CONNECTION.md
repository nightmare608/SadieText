# Firebase bağlantısı — 18 Ekim 2026

Gönderilen google-services.json yerel projede app/google-services.json konumuna
eklendi. Android paket adı tr.sakin.chat ile eşleşti. Google Services görevinin
ürettiği project_id ve google_app_id, gerçek dosyayla karşılaştırılıp doğrulandı.
BuildConfig.HAS_FIREBASE=true: APK, açılış ekranından sonra gerçek hesap akışını açar.

## Kotlin DSL

Gerekli ayarlar zaten mevcut olduğundan eklenti/bağımlılıklar yinelenmedi ve
mevcut Android, Compose, Signal ve Firebase sürümleri korundu.

Proje kökü build.gradle.kts içinde:

```kotlin
id("com.google.gms.google-services") version "4.4.3" apply false
```

app/build.gradle.kts içinde:

```kotlin
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) apply(plugin = "com.google.gms.google-services")
```

Aynı modülün dependencies bloğunda:

```kotlin
implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
implementation("com.google.firebase:firebase-auth")
implementation("com.google.firebase:firebase-firestore")
```

Koşullu eklenti, ayar dosyası olmayan açık kaynak kopyasının kurulum önizlemesiyle
derlenebilmesini korur. Functions, Storage veya SMS bağımlılığı eklenmedi; Spark
kotaları geçerlidir. Konsoldaki faturalandırma planı bu dosyayla okunamaz ve testte
değiştirilmedi. Kaynak ZIP'e JSON veya servis hesabı anahtarı eklenmez.

## Canlı salt okunur test

Yalnız GET istekleri yapıldı; hesap veya belge oluşturulmadı/değiştirilmedi.
API anahtarı rapora veya çıktıya yazılmadı.

- Authentication proje ayarı uç noktası: HTTP 400, CONFIGURATION_NOT_FOUND.
  Authentication kurulumu henüz tamamlanmamış görünüyor.
- Firestore kimliksiz okuma yoklaması: HTTP 403, SERVICE_DISABLED.
  Cloud Firestore API etkin değil veya yeni etkinleştirme henüz yayılmamış.
  Bu sonuç, canlı güvenlik kurallarının çalıştığını kanıtlamaz.

Android/Firebase derleme bağlantısı doğrulandı. Canlı kayıt, e-posta doğrulama,
yetkili Firestore okuma/yazma ve iki telefonla mesaj teslimi henüz doğrulanamadı.

## Konsolda tamamlanacaklar

1. Authentication → Başlayın / Get started → Sign-in method → Email/Password.
   E-posta ve şifre sağlayıcısını açıp kaydedin.
2. Firestore Database → Veritabanı oluştur. Spark uyumlu Standard/Native,
   varsayılan (default) veritabanını üretim modunda oluşturun.
3. Firestore → Kurallar bölümüne proje kökündeki firestore.rules içeriğinin
   tamamını yapıştırıp yayımlayın.
4. İndeksler bölümünde chats koleksiyonu için bileşik indeks oluşturun:
   members: Array contains; updatedAt: Descending. Sorgu kapsamı: Collection.
   Tanım firestore.indexes.json dosyasındadır. İndeksin hazır olmasını bekleyin.
5. Alternatif olarak FIREBASE.md içindeki CLI komutuyla kuralları ve indeksleri
   birlikte yükleyin. Bu adım proje sahibi oturumu ister; API anahtarı yönetici
   yetkisi sağlamaz ve bu teslimde konsol ayarları değiştirilmedi.
6. Yeni APK'de iki hesapla kayıt ve doğrulamayı deneyin. İki cihazda güvenlik
   numarası karşılaştırıldıktan sonra mesaj teslimini kontrol edin.

## JSON'u kendiniz eklerken

1. Kaynak ZIP'i açıp SadieText klasörünü Android Studio'da açın.
2. Dosyayı tam olarak SadieText/app/google-services.json konumuna kopyalayın.
   src içine veya proje köküne koymayın; adında (1) gibi ekler olmasın.
3. Gradle Sync yapın, ardından ./gradlew clean assembleDebug çalıştırın.
4. APK app/build/outputs/apk/debug/app-debug.apk konumunda oluşur.

Gönderdiğiniz dosya yerel projeye eklendi; kaynak ZIP ve Git dağıtımından hariç
tutulur. Yeniden derlemek için orijinal dosyanızı saklayın. Servis hesabı gerekmez.
