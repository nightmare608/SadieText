# SadieText — Firestore kurallarını yayımlama

Sohbet ekranındaki genel hata için [FIRESTORE_DIAGNOSTICS.md](FIRESTORE_DIAGNOSTICS.md):
kurallar ve indeksler ayrı yayımlanır. Bu dosyanın önceki 73 test sonucu kurallara
aittir; yeni tanılama kaynak sürümünün derlendiği anlamına gelmez.

## Firebase Console (telefondan da yapılabilir)

1. https://console.firebase.google.com/ adresinde **sadietext** projesini açın.
2. **Build → Firestore Database → Rules (Kurallar)** bölümüne girin.
   Uygulamanın kullandığı veritabanı **(default)** olmalıdır.
3. Projedeki **firestore.rules** dosyasını metin olarak açıp tamamını kopyalayın.
   Konsoldaki mevcut kuralların tamamını bununla değiştirin. Markdown kod çitlerini
   veya bu rehberi kurallar editörüne yapıştırmayın.
4. **Publish (Yayımla)** düğmesine basın; başarı bildirimi ve yayımlanan sürümün
   tarihini kontrol edin. Production Mode seçilmiş olması bu dosyanın otomatik
   olarak yayımlandığı anlamına gelmez.
5. **Bu güvenlik düzeltmesi için 0.2.2-preview (versionCode 4) APK'yi kurun.**
   Önceki APK sohbet oluştururken var olmayan belgeyi okumaya çalışıyordu;
   yeni kurallar bu okumayı engeller. Güncellenmiş sürüm önce okuma yapmadan,
   kurallarda doğrulanan kimlikle yeni sohbeti oluşturur. Mevcut sohbetler
   üyelik sorgusuyla bulunup eski belge kimlikleriyle açılır. Hesabı veya verileri silmeyin.

## Sohbet listesi indeksi

Firestore Database → **Indexes → Composite → Create index** bölümünde:

| Ayar | Değer |
| --- | --- |
| Collection ID | chats |
| members | Arrays / Array contains |
| updatedAt | Descending |
| Query scope | Collection |

Durum **Enabled** olana kadar bekleyin. İndeks eksikliği bir izin hatasından
farklıdır. CLI kullanılıyorsa aşağıdaki komut indeksleri de kurar. Büyük şifreli
alanların indeks istisnaları da proje kökündeki firestore.indexes.json içindedir.

## Bilgisayardan alternatif yayımlama

Proje kökünden, Node.js 22 ve JDK 21 ile:

```sh
cd firebase
npm ci
npm run emulator:test
npx firebase login
npx firebase deploy --project sadietext --config ../firebase.json --only firestore:rules,firestore:indexes
```

Bu komutlar servis hesabı anahtarı gerektirmez. Spark ile uyumludur;
Cloud Functions veya ücretli bir arka uç eklenmez.

## Korunan veri yapısı

- `users/{uid}` yalnız görünen isim, kullanıcı adı ve **açık** Signal anahtar
  paketidir. Doğrulanmış kullanıcı tek bir profili okuyabilir; profilleri
  listeleyemez. Fazladan alan içeren eski/yönetici profilleri diğer kullanıcılara
  kapatılır; sahibi kendi belgesini okuyabilir. Profilin yalnız sahibi, izin verilen
  anahtar alanını güncelleyebilir.
- `handles/{username}` tam kullanıcı adı araması içindir; listeleme kapalıdır.
- `chats/{id}` tam iki ayrı katılımcı ile oluşturulur. Üyeler sonradan değişmez.
  Yalnız o sohbetin katılımcıları sohbeti ve altındaki mesajları okuyabilir/yazabilir.
- Mesajın göndereni oturum sahibidir; alıcı diğer katılımcıdır. Kimlik anahtarları
  güncel profillerle eşleşmelidir. Yeni mesaj için sunucu saati ve tam alan şeması gerekir.
- Gövde sonradan değiştirilemez. Yalnız alıcı bir kez okundu zamanı yazabilir;
  yalnız gönderen kendi sunucu mesajını silebilir.
- Silme işareti hesabı sohbet erişiminden çıkarır ve eski token ile yeniden profil
  oluşturmayı engeller. Yeni mesajlar silinmiş alıcıya gönderilemez.
- Sohbet sorguları en fazla 50, mesaj sorguları en fazla 100 belgeyle sınırlıdır;
  uygulamanın normal sayfası 50, hesap silme temizliği 100 kullanır.
- Tanımlanmamış tüm yollar ve alt koleksiyonlar kapalıdır.

Firestore belge okumasında alanları gizleyemez. Bu yüzden `users` belgesinde
e-posta, şifre veya özel anahtar **bulunmamalıdır**. İstemcinin bu alanları
eklemesi kurallarla reddedilir. E-posta/şifre Firebase Authentication'dadır;
özel anahtarlar cihazın korunan yerel kasasındadır. Yönetici SDK'sı ve konsol
kuralları atlayabildiği için yönetici olarak da profil belgesine özel bilgi
eklemeyin. Daha önce elle eklediyseniz yayımlamadan önce kaldırın.

## Yeniden güvenlik incelemesi — 18 Ekim 2026

### Bulunan iki varlık sorgulama yolu ve düzeltmeleri

1. Eski `active() && !exists(chats/id)` get istisnası, var olmayan belgeye izin
   verirken başkasının mevcut belgesine erişimi reddediyordu. İstisna kaldırıldı.
   Var olmayan sohbetin olası katılımcısı da oluşturma öncesi get yapamaz.
2. Yalnız bu istisnayı kaldırmak yeterli değildi: kullanıcı kendi UID'sini içeren
   bir sohbeti tahmin edilen ID'ye yazmayı deneyerek boş/dolu ayrımı yapabilirdi.
   Create artık ID'nin `p2:` + ilk UID + `:` + ikinci UID olmasını zorunlu
   tutar. Üyeler farklı, sıralı ve ayırıcı içermeyen UID'ler olmalıdır. Böylece
   saldırgan kendi üyesi olduğu bir çiftle başka çiftin ID'sini işgal edemez.

İki regresyon testi eski kurallarda **beklendiği gibi başarısız**, düzeltilmiş
kurallarda başarılı oldu. Yalnız yasaklı testler değil, izinli oluşturma ve
yeniden açma işlemleri de test edildi.

### Android uyumluluğu ve kimlikler

`ChatTransport.open`, sıralı iki UID'ye tam eşit `members` filtresiyle,
`limit(2)` kullanarak yalnız kendi çiftine ait mevcut sohbeti arar. Bu sorgu boş
olduğunda da yetkilendirilebilir; var olmayan belgeye doğrudan get yapılmaz.
Mevcut sohbet varsa ID aynen kullanılır; eski format ID varsa önceliklidir.
Mesajlar taşınmaz, yeniden şifrelenmez veya silinmez. Böylece şifreli mesaj
bağlamındaki eski sohbet ID'si, yerel geçmiş ve bekleyen zarflar korunur.
İki sonuç sınırı normalde en fazla bir eski ve bir yeni belge olabileceği
varsayımına dayanır; yönetici aynı çift için çok sayıda kopya oluşturmamalıdır.

Sorgu boşsa yeni `p2:uidA:uidB` belgesi önce okunmadan set edilir. Create
kuralı ID'yi üyelere bağlar. Eşzamanlı ilk açma aynı belgeye yönelir; ikinci
set ancak aynı üyeler/isimler korunup updatedAt değişirse kabul edilir.
Mevcut sohbeti normal açmak updatedAt zamanını değiştirmez.

Mesaj gönderme transaction'ı korunur: mevcut sohbetin katılımcısı var olmayan
mesajı okuyabilir; bu izin sohbetin dışındaki kimseye verilmez. Yetkisiz kullanıcı
hem var olan hem var olmayan mesaj/ebeveyn için `permission-denied` alır.

Kuralların tamamında **hashing fonksiyonu kullanılmaz**. Yeni kimlik doğrulama
sadece string birleştirme, sıralama ve UID karakter doğrulaması kullanır.
Eski hash ID'lerinin yalnız mevcut belgeler üzerindeki üye erişimi sürer;
yeni belgelerin eski hash formatıyla oluşturulması reddedilir. Eski APK yeni
sohbet oluşturamayacağından iki taraf da 0.2.2-preview sürümüne geçmelidir.

Sohbet kimliği gizli kabul edilmez. UID'leri bilen biri ID'yi üretebilir;
yetkisiz kişilerin belge varlığını veya içeriğini öğrenmesini üyelik ve ID
bağlaması engeller. Zamanlama/altyapı yan kanallarına karşı biçimsel gizlilik
ispatı yapılmış değildir.

UID karakter kümesi A-Z, a-z, 0-9, alt çizgi ve kısa çizgi; uzunluk 1–128'dir.
İki nokta UID içinde yasaktır; `uidA:uidB` birleşimi böylece belirsiz değildir.
Mevcut e-posta/şifre Auth sağlayıcısının UID'leri desteklenir. İleride özel UID
ile başka sağlayıcı eklenirse bu politika ayrıca incelenmelidir. Kotlin ve
emülatör `p2:alice:bob` ve `p2:Alice_1:bob-2` vektörlerini doğrular.

### Atomik kayıt ve getAfter / existsAfter

- `handles.create`, işlem öncesinde profilin olmamasını ve işlem sonunda profilin
  kullanıcı adının bu handle'a dönmesini zorlar. Ek alias oluşturulamaz.
- `users.create`, işlem sonundaki handle'ın sahibinin kendi UID'si olmasını
  zorlar. Farklı hesap veya farklı handle bağlamak reddedilir.
- Silme işareti işlem sonu durumunda da denetlenir; aynı batch içinde kayıt ve
  silme işareti oluşturma reddedilir. Güncel oturumlu gerçek kayıt transaction'ı
  ve aynı kullanıcı adını yarışarak alma testi geçer; yalnız bir hesap kazanır.
- Handle/profil silme ve silme işareti oluşturma aynı atomik son durumu gerektirir.
  Yeni kimlik değişimi ve hesap silme yakın zamanda doğrulanmış oturum ister.
- getAfter belge okumak için kullanıcıya izin vermez; yalnız kurallar içindeki
  atomik işlem sonu denetimidir. Alt yazmaların her biri kendi kuralını da geçer.

### Sorgular ve sınırlar

Üyelik koşulu list sorguları tarafından kanıtlanabilecek şekilde tutuldu;
listeleme için üyelik filtresi ve limit gereklidir. Android'in sıralı sohbet
listesi, cursor sayfalaması, sıralı mesaj listesi, gönderene göre mesaj temizliği
ve belge kimliğine göre hesap temizliği sorguları emülatörde geçti. Sınırlı olsa
da filtresiz veya başka kullanıcının üyeliğine göre sorgu reddedilir. Global
Yeni tam-çift eşitlik sorgusu mevcut tek alan indeksini kullanır; members alanının
otomatik indekslerini kapatmayın. messages collection-group sorgusu kapalıdır. Foreign/absent belge ID filtresi
eklenmiş sorgular da her iki durumda reddedilir.

Kural bağımlı okuma yolları normal chat create için dört, message create için
beş farklı belgeyi kullanır; tekrar erişimler aynı istekte önbelleklenir. Aynı
sohbetteki 100 mesajlık gerçek silme batch'i de test edildi. Emülatör bileşik
indeks gereksinimini tam olarak zorlamaz; yukarıdaki indeks canlıda gereklidir.

## Test sonucu

Gerçek Firestore Emulator üzerinde **73 testin 73'ü geçti**, süreç çıkış kodu 0.
Testler `firebase/tests/firestore.test.mjs`, özet `docs/verification/firestore-summary.txt`.
Üçüncü kişi, oturumsuz/doğrulanmamış kullanıcı, gönderen taklidi, grup/öz sohbet,
profil müdahalesi, özel alanlar, kapalı yollar, kimlik değişimi, hesap silme,
okundu makbuzları, sayfa sınırları ve izin verilen iki yönlü gönderim test edildi.
Reddedilmesi beklenen isteklerin PERMISSION_DENIED logları test başarısızlığı değildir.

Bu sonuç yerel emülatöre aittir; kurallar bu çalışma sırasında canlı projeye
yayımlanmadı. Yayımladıktan sonra iki doğrulanmış hesapla kullanıcı adı seçme,
arama ve mesajlaşmayı deneyin. Üçüncü bir hesapla aynı sohbet belgesini okumayı
Console Rules Playground üzerinden deneyip reddedildiğini kontrol edin.
Console'un Data editörü yönetici erişimidir; izin testi olarak kullanılamaz.

Kurallar ciphertext alanındaki verinin gerçekten şifreli olduğunu kanıtlamaz;
E2EE istemcideki libsignal doğrulamasına dayanır. Bu testler bağımsız güvenlik
denetimi veya gerçek cihazlar üzerinde uçtan uca test yerine geçmez.

Resmî kaynaklar:
- https://firebase.google.com/docs/firestore/security/rules-fields
- https://firebase.google.com/docs/firestore/security/test-rules-emulator
- https://firebase.google.com/docs/firestore/security/rules-query
- https://firebase.google.com/docs/firestore/security/rules-conditions
