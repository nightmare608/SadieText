# Sohbet listesi hatası — 19 Ekim 2026

## Doğrulanabilen durum ve sınır

Bu çalışmada kullanıcının telefonuna ADB/Logcat bağlantısı yoktu. Gerçek canlı
hata kodu görülmedi; PERMISSION_DENIED veya FAILED_PRECONDITION olduğu kesin
olarak iddia edilmez. Önceki 0.2.2 istemcisi FAILED_PRECONDITION, INVALID_ARGUMENT
ve başka kodları aynı genel mesaja çeviriyordu. PERMISSION_DENIED zaten farklı
bir metinle gösterildiğinden, bildirilen tam genel metin eksik indeks ihtimalini
güçlendirir; bu bir çıkarımdır, cihazdan alınmış hata kaydı değildir.

Sohbet listesi sorgusu korunmuştur:

```kotlin
db.collection("chats").whereArrayContains("members", uid)
  .orderBy("updatedAt", Query.Direction.DESCENDING).limit(50)
```

Üyelik filtresi veya tarih sıralaması kaldırılmadı. Son 50 sohbet ve cursor ile
eski sayfalar korunur. Eksik indeksi gizlemek için ilk 50 belgeyi farklı bir
sıralamayla almak eski sohbetleri kaybettirebileceğinden böyle bir geri dönüş
yoktur. Kurallar gevşetilmedi; limit 50 ile uyumludur.

## İlk kontrol: kurallardan ayrı olan indeks

Kuralları yayımlamak indeksi otomatik oluşturmaz. Firebase Console'da
**sadietext → Firestore Database → (default) → Indexes → Composite → Add index**:

- Collection ID: **chats**
- Alan 1: **members**, mod **Array contains / Arrays**
- Alan 2: **updatedAt**, mod **Descending / Azalan**
- Query scope: **Collection**

Durum **Enabled** olana kadar bekleyin. Alan adı büyük/küçük harfe duyarlıdır.
Sonra mevcut APK'yi tamamen kapatıp açın. Yeni 0.2.3 kaynak derlemesinde ise
**Yeniden bağlan** düğmesi dinleyiciyi aynı sayfada tekrar başlatır.

CLI alternatifi, proje kökünden:

```sh
cd firebase
npm ci
npx firebase login
npx firebase deploy --project sadietext --config ../firebase.json --only firestore:indexes
```

Bu işlem için proje yöneticisi hesabı gerekir. google-services.json içindeki
Android API anahtarı yönetici yetkisi vermez. Bu çalışmada canlı indekslerin
listesi okunmadı ve yeni indeks oluşturulmadı. Dosyada tanımın olması canlıda
Enabled olduğunu kanıtlamaz. Emülatör bileşik indeks eksikliğini zorlamaz.

## Gerçek hata kodunu Logcat'te alma

Telefonu USB hata ayıklama ile kendi bilgisayarına bağlayın. Parola, token,
servis hesabı veya anahtar paylaşmayın. Android Studio → Logcat'te uygulama
paketi **tr.sakin.chat**, etiket **Firestore** veya yeni kaynak derlemesinde
**SadieTextDb** filtresini seçin. Uygulamayı açıp hatayı yeniden oluşturun.

Terminal alternatifi:

```sh
adb devices
adb logcat -v time -s Firestore:W SadieTextDb:W '*:S'
```

Yeni tanılama örneği (örnek kayıttır; kullanıcının cihazından alınmamıştır):

```text
W/SadieTextDb: operation=CHAT_LIST code=FAILED_PRECONDITION reason=INDEX_REQUIRED
```

- **FAILED_PRECONDITION + INDEX_REQUIRED:** yukarıdaki bileşik indeksi oluşturun.
- **FAILED_PRECONDITION + INDEX_BUILDING:** Enabled olmasını bekleyin.
- **FAILED_PRECONDITION + PRECONDITION:** başka bir sunucu koşuludur; otomatik
  olarak eksik indeks sayılmaz. SDK'nın asıl Firestore kaydını inceleyin.
- **PERMISSION_DENIED:** e-posta doğrulaması güncel token'a yansımış mı, kendi
  `users/{uid}` profili var mı, `deletedAccounts/{uid}` silme işareti var mı,
  doğru `(default)` veritabanına ve projeye kurallar yayımlanmış mı kontrol edin.
  Chats sorgusu members array-contains kendi UID'si ve limit 50 kullanmalıdır.
  Çözüm olarak herkese okuma/yazma izni vermeyin.
- **INVALID_ARGUMENT / OUT_OF_RANGE:** sorgu veya veri geçersiz; işlem adı ve
  hata koduyla istemciyi kontrol edin.
- **RESOURCE_EXHAUSTED:** kota veya istek sınırı.
- **UNAVAILABLE / DEADLINE_EXCEEDED:** ağ/hizmet erişimi. Bekleyen mesajlar silinmez.

## Kaynakta yapılan düzeltmeler

- Gerçek Firebase kodu korunur, işlem adıyla birlikte UI'de destek kodu görünür.
- Eksik indeks, hazırlanmakta olan indeks, diğer ön koşullar ve izin hataları
  ayrı mesajlara çevrilir. İç içe sarılmış Firebase hataları da tanınır.
- Logcat'e yalnız enum işlem adı, kod ve sınıflandırma yazılır. İstisnanın ham
  metni, sorgu, UID, belge yolu, e-posta, mesaj veya anahtar loglanmaz.
- Yeniden bağlan dinleyiciyi önce iptal edip yeniden açar; hesap, anahtarlar ve
  yerel geçmiş sıfırlanmaz. İptal edilen coroutine hata olarak sunulmaz.
- Veri alınamadığında yanlışlıkla “Henüz sohbet yok” yazılmaz.

## Bu teslimdeki doğrulama

Yeni tanılama kaynak sürümü **0.2.3-preview / versionCode 5** olarak hazırlandı.
**Yeni APK bu çalışmada üretilemedi.** Önceki geçerli APK 0.2.2-preview'dür;
ona yeni tanılama özelliklerinin eklendiği iddia edilmez.

Önceki geçici Gradle/JDK21/Android SDK kurulumu bu oturumda mevcut değildi.
Gradle çalıştırma araç bulunamadığından başlamadı. Firestore emülatör JAR indirmesi
tamamlanamadı; bir süreç kontrolü otomatik onay incelemesi kapasite hatasıyla
engellendi. Yeni Kotlin testleri ve 75 Firestore testi bu çalışmada çalıştırılmış
sayılmaz. Önceki 73 emülatör ve 14 JVM sonucu yalnız önceki sürüme aittir.
Mevcut firestore.rules ve firestore.indexes.json değişmedi.

Bu çalışmada emülatörsüz indeks dosyası testi ve JS sözdizimi kontrolü çalıştırıldı.
Yeni testler: gerçek Firebase SDK sınıflarıyla 10 Android hata sınıflandırma/gizlilik/iptal testi, gerçek dinleyici
üzerinden izinli 50 ve reddedilen 51 belge sorgusu, dağıtım indeksi kontrolü.

Derleme ortamı hazır olduğunda proje kökünden:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
# Bağlı Android telefon/emülatör ile yeni tanılama testleri:
./gradlew connectedDebugAndroidTest
cd firebase
npm ci
npm run emulator:test
```

Resmî kaynaklar:
- https://firebase.google.com/docs/firestore/query-data/indexing
- https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/FirebaseFirestoreException.Code
