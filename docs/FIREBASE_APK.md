# GitHub üzerinden Firebase APK

1. GitHub deposunda Settings → Secrets and variables → Actions → New repository secret.
2. Ad: `GOOGLE_SERVICES_JSON`. Değer: Firebase Console'dan indirdiğiniz `google-services.json` dosyasının tam içeriği. Servis hesabı anahtarı değil, Android uygulama yapılandırması olmalıdır. Proje `sadietext`, paket `tr.sakin.chat`.
3. Dosyayı commit etmeyin. Önceden kullanılan base64 biçimi için `GOOGLE_SERVICES_JSON_B64` desteklenir; ikisi varsa JSON önceliklidir.
4. Actions → Android preview APK → Run workflow → `mode=firebase`. Ya da ana dala commit gönderin. Eksik/yanlış ayar derlemeyi açık hatayla durdurur.
5. Başarılı Android ve kural testlerinden sonra Releases'ta `firebase-preview-…` sürümünden `SadieText.apk` indirin.

Yerel derleme: dosyayı `app/google-services.json` konumuna koyup `./gradlew -PrequireFirebase=true assembleDebug` çalıştırın.

## Sunucu ayarları

Email/Password sağlayıcısı, Production Mode Firestore, `firestore.rules` ve `firestore.indexes.json` gerekir. Kuralları yayımlamak indeks oluşturmaz. Firebase CLI ile doğru projede `firebase deploy --only firestore:rules,firestore:indexes --project sadietext` çalıştırılabilir. Bu CI iş akışı canlı sunucu ayarlarını değiştirmez. İndeks hazır olana kadar sohbet sorgusu hata verebilir; tanılama rehberindeki adımları kullanın.

## İmza ve cihaz verileri

Bu iş akışı geçici runner debug imzası kullanır; ayrı çalıştırmalarda imza değişebilir. Kalıcı dağıtım için aynı, yedeklenmiş üretim keystore'u ile imzalayın; anahtarı repoya koymayın. Eski APK'nin imzası farklıysa Android güncellemeyi reddeder. Uygulamayı kaldırmak yerel özel anahtarları kaybettirebilir; eski şifreli geçmiş kurtarılamayabilir. Güncelleme hatasında verileri silmeden önce imza planını çözün.

## Doğrulama sınırı

Derleme ve emülatör testleri canlı Firebase projesinde iki telefondan yazışma yapıldığını kanıtlamaz. Bunun için kayıt, doğrulama, kullanıcı adı, sohbet açma, güvenlik numarası karşılaştırma, mesaj/okundu ve yeniden bağlantı adımları iki gerçek cihazla ayrıca test edilmelidir. Bağımsız güvenlik denetimi tamamlanmadı.
