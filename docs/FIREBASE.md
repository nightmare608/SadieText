# Firebase Spark kurulumu

Mevcut proje için Console üzerinden kuralları yayımlama, indeks ve test rehberi:
[FIRESTORE_RULES.md](FIRESTORE_RULES.md).

Bu adımlar uygulama sahibi tarafından kendi Google hesabıyla yapılmalıdır.
Projeye bir servis hesabı, yönetici anahtarı veya şifre eklemeyin.

1. https://console.firebase.google.com/ adresinden bir proje oluşturun.
   Spark planında kalın; ödeme hesabı bağlamayın. Analytics zorunlu değildir.
2. Android uygulaması ekleyin. Paket adı **tr.sakin.chat** olmalıdır.
3. İndirilen `google-services.json` dosyasını yerel **app/** klasörüne koyun.
   GitHub'a göndermeyin. Uygulama sırrı yerine Android Firebase bağlantı ayarları
   içerir; servis hesabı dosyasıyla karıştırmayın.
4. Authentication → Sign-in method → **Email/Password** sağlayıcısını etkinleştirin.
   Gmail adresleri de bu yöntemle kaydolabilir. Google/OAuth sağlayıcısı bu sürümde kullanılmaz.
5. Authentication → Templates bölümünde doğrulama ve şifre sıfırlama e-postalarının
   gönderici/proje adını ayarlayın. Firebase'in konsolda gösterdiği hizmet/eposta
   kotalarını kontrol edin. E-posta doğrulanmadan veritabanına erişim açılmaz.
6. Firestore **Standard / Native** veritabanını oluşturun; test modunu kullanmayın.
   Konumu dikkatle seçin. İstemcinin kullandığı varsayılan `(default)` veritabanıdır.
7. Proje kökünde Firebase CLI kullanarak kuralları ve indeksleri gönderin:

```
cd firebase
npm ci
npx firebase login
npx firebase deploy --project KENDI_FIREBASE_PROJE_ID --config ../firebase.json --only firestore:rules,firestore:indexes
```

`KENDI_FIREBASE_PROJE_ID` değerini konsoldaki gerçek proje kimliğinizle değiştirin.
İndeks oluşturma tamamlanana kadar bekleyin. Kurallar dosyasını konsoldan elle
kopyalayabilirsiniz ancak indeks tanımı da yüklenmelidir.

8. Android Studio'da Sync, ardından APK'yi yeniden derleyin. JSON dosyası
   yokken `BuildConfig.HAS_FIREBASE=false` olur; gerçek uygulama açılmaz.
9. İki ayrı hesap/telefonla kayıt, doğrulama ve güvenlik numarası akışını deneyin.
   Firestore mesaj belgelerinde yalnız ciphertext ve metadata olmalıdır.

## GitHub Actions ile yapılandırılmış APK

Yerel JSON dosyasını base64 biçimine dönüştürüp deponun Actions secrets bölümünde
`GOOGLE_SERVICES_JSON_B64` adıyla saklayın. İş akışı bunu geçici `app/google-services.json`
dosyasına dönüştürür. Dosyanın/secret'ın içeriğini loglara yazdırmayın.
Bu secret olmadan CI yalnız kurulum/demolu APK derler.

## Şema

- `handles/{kullaniciAdi}`: yalnız uid; tam belge sorgusu, listeleme yok.
- `users/{uid}`: görünen isim, kullanıcı adı, herkese açık Signal anahtar paketi.
  E-posta, şifre veya özel anahtar burada bulunmaz.
- `chats/{id}`: iki uid, sohbet içi kullanıcı adı etiketleri, güncelleme saati.
- `chats/{id}/messages/{uuid}`: şifreli içerik, Signal türü, gönderen/alıcı
  uid ve açık kimlik anahtarı tanımları, sunucu saati ve okundu zamanı.
- `deletedAccounts/{uid}`: özel, değiştirilemeyen silme işareti; eski oturum
  jetonlarının aynı uid ile profili yeniden oluşturmasını engeller.

Uygulama üyelik veya makbuzları sadece arayüzde kontrol etmez; kurallar sunucuda
zorlar. Kurallar şifreli verinin gerçekten Signal çıktısı olduğunu kanıtlayamaz;
karşı tarafın libsignal doğrulaması ve güvenlik numarası karşılaştırması gerekir.

## Maliyet

Resmi Firestore ücretsiz kota tablosunda günlük 50.000 okuma, 20.000 yazma,
20.000 silme ve 1 GiB saklama alanı belirtilir. Kotalar Pasifik saatiyle gece
yarısı civarında yenilenir. Güncel tablo:
https://firebase.google.com/docs/firestore/quotas

Bir mesaj normalde mesaj belgesi + sohbet saati + alıcının okundu makbuzu için
**3 yazma** oluşturabilir. Bu yüzden 20.000 yazma “20.000 mesaj” demek değildir.
Kimlik kontrolü, işlem tekrarları, açılan sayfalar ve kurallardaki bağımlı belge
okumaları da tüketim yaratır. 100 kullanıcı için yeterlilik trafikle ölçülür.

Gerçek zamanlı dinleyici yalnız açık liste veya açık sohbette aktiftir;
uygulama arka plana geçince kaldırılır. Sayfa boyutu 50'dir. Büyük şifreli
alanlar indekslenmez. Spark kota dolduğunda hizmet aksayabilir; uygulama hata
mesajı gösterir ve şifreli giden kutusunu saklar. Hiçbir ücretli Cloud Functions,
Storage, TTL, yedekleme veya ücretli sunucu bileşenine bağımlılık yoktur.
