# SadieText çevrimdışı önizleme

Firebase bağlantısını sonra tamamlamak için ayrı bir önizleme modu eklendi.
Bu teslim **kaynak kodudur**; yeni modun APK derlemesi ve cihaz testleri bu
ortamda henüz çalıştırılamadı. XML ve iş akışı YAML sözdizimi kontrolleri geçti.

## Modun davranışı

- Sadie Sink açılış yazısı ve iki Kabul et seçeneği korunur.
- Ardından doğrudan örnek sohbetler ekranı açılır; Firebase kurulum/giriş ekranı yoktur.
- `deniz` veya `ece` adıyla örnek kişi arayıp yerel mesaj ekleme/silme ve temaları deneyebilirsin.
- Gerçek kişilere mesaj gönderilmez. Demo mesajları yalnız uygulama belleğindedir;
  uygulama süreci kapandığında kaybolur. Bunları gerçek özel mesajlar için kullanma.
- Ayrı paket: **tr.sakin.chat.offline**, ad: **SadieText Çevrimdışı**.
  Normal uygulama **tr.sakin.chat** olarak kalır; hesabı, yerel kasası ve geçmişi etkilenmez.
- `google-services.json` mevcut olsa bile çevrimdışı modda Google Services
  eklentisi uygulanmaz; JSON'un silinmesi veya yeniden adlandırılması gerekmez.
- Manifestte INTERNET ve ACCESS_NETWORK_STATE izinleri ile FirebaseInitProvider
  kaldırılacak şekilde ayarlandı. Firebase/Auth/Firestore kaynakları ve derleme
  bağımlılıkları gelecekteki normal sürüm için ortak projede korunur.

## Android Studio / terminal

Proje kökünde, JDK 21 ve Android SDK 35 / Build Tools 35.0.0 / NDK 27.0.12077973 ile:

```sh
./gradlew -PofflinePreview=true assembleDebug testDebugUnitTest lintDebug
```

Çıktı: `app/build/outputs/apk/debug/app-debug.apk`.
Bu komut uygulamanın çevrimdışı çalışma modunu seçer; Gradle'ın bağımlılıkları
ilk kez indirmesi için hâlâ internet gerekebilir. Bu ortamda derleme araçlarının
indirilememesi bu nedenle Firebase'den bağımsız bir engeldir.

Gerçek telefon/emülatör ile:

```sh
./gradlew -PofflinePreview=true connectedDebugAndroidTest
```

`DemoFlowTest`, örnek kişi arama/mesaj ekleme-silme akışına ek olarak kurulu
paketin tr.sakin.chat.offline olduğunu, internet izni ve Firebase otomatik
başlatıcısı bulunmadığını denetler. Test kodu eklendi; cihaz testi yapılmış sayılmaz.

## GitHub Actions

Proje bir GitHub deposuna aktarıldığında **Actions → Android preview APK →
Run workflow → mode: offline** seçilebilir. Firebase secret'ı gerekmez.
İş akışı çevrimdışı APK'yi ve test APK'sini derleyip JVM/lint kontrollerini
çalıştıracak şekilde hazırlandı. Bağlı Android cihaz testlerini bu iş akışı
çalıştırmaz. Bu çalışmada GitHub'da çalıştırılmış bir sonuç yoktur.

## Eve dönünce normal sürüm

```sh
./gradlew assembleDebug
```

`-PofflinePreview=true` vermediğinde mevcut Firebase bağlantılı yapılandırma
korunur. Kendi `app/google-services.json` dosyan ve yayımlanmış kurallar/indeksler
ile devam edebilirsin. Şu anki üretim veritabanına bu değişiklikle yazma yapılmadı.
