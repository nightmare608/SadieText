# Testler

## Derleme ve JVM kriptografi testleri

JDK 21, SDK 35 / Build Tools 35.0.0 ve NDK 27.0.12077973 ile:

```
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest assembleRelease
```

`SignalEngineTest` gerçek libsignal JNI/Linux kütüphanesini kullanır; şifreleme
sahteleştirilmez. Testler gerçek iki telefon/Firebase çalışmasının yerine geçmez.

Kapsam: karşılıklı mesaj ve ratchet yanıtı, süreç yeniden açılması, karşılıklı
aynı güvenlik numarası, açık onay gereksinimi, kimlik değişiminde durma, kalıcı ve
idempotent giden kutusu, bozulmuş ciphertext, sohbet/mesaj bağlamı, başarısız yerel
kayıtta geri alma, tekrarlanan ve sırasız teslim, haftalık ön anahtar yenilemesi.

## Firestore erişim testleri

```
cd firebase
npm ci
npm run emulator:test
```

Yerel `demo-sakin` emülatöründe çalışır; canlı hesaba yazmaz. Firebase SDK'daki
özel bağlamlarla doğrulanmış, doğrulanmamış ve oturumsuz kullanıcılar test edilir.
Bu, gerçek Firebase Auth kayıt/e-posta teslim testi değildir.

Güvenlik yeniden incelemesi: foreign/absent get ve yazma cevapları, ID'nin üyelere
bağlanması, okuma yapmadan sohbet açma/yarış, gerçek kayıt ve gönderim transaction'ları,
eşzamanlı handle rezervasyonu, getAfter/existsAfter atomik son durumları, Android
sorguları ve cursor'ları, 100 mesajlık silme batch'i test edilir. ChatIdTest JVM
testi kurallar ile paylaşılan p2 kimliklerini, sıralamayı ve ayırıcı karakter
reddini kontrol eder. Eski ID ile geçmiş/okundu/silme/bekleyen mesaj gönderimi,
tam-çift sorgusu, eski ve yeni format eşzamanlı ilk açma da test edilir.

Kapsam: iki katılımcı erişimi, üçüncü kişi ve oturumsuz erişimin reddi, kullanıcı
listelerinin reddi, katılımcı/gönderen değiştirme, düz metin alanı, makbuz ve silme
yetkileri, zaman/boyut kısıtları, sınırlı sorgular, atomik handle rezervasyonu ve
silme, eski jetonla yeniden profil oluşturmanın reddi, cihaz kimliği ve yakın
giriş koşulu. Kurallar şifreli alanın gerçek protokol çıktısı olduğunu kanıtlamaz.

## Android cihaz testleri

```
./gradlew connectedDebugAndroidTest
```

Android 8+ telefon/emülatör gerekir. Bu teslimde test APK'si derlenmiştir;
cihaz testlerinin çalıştırıldığı iddia edilmez.

- `DemoFlowTest`: yapılandırma ekranından arayüz demosu, kullanıcı arama,
  yerel mesaj ekleme/silme ve listeye geri dönme. Firebase dosyası olmayan derleme içindir; yapılandırılmış derlemede bu demo testi atlanır.
- `AndroidSecurityTest`: Android Keystore kasası aç/kapat, diskte açık metin
  bulunmaması, değiştirilmiş verinin reddi, Android native Signal ile şifreleme
  ve Keystore üzerinden yeniden açılan cihaz deposunda çözme.

## Gerçek iki telefonla kabul

Kendi Firebase projeniz yapılandırıldıktan sonra ayrı hesaplarla:

1. Kayıt → doğrulama → benzersiz kullanıcı adı. Aynı adı eşzamanlı almaya çalışın.
2. Arkadaşı bulun; ilk mesajdan önce iki cihazın güvenlik numarasını karşılaştırın.
3. Birbirinize Türkçe/emoji ve 2000 karaktere kadar mesaj gönderin; saat,
   gönderildi/okundu ve arka plan/ön plan davranışını kontrol edin.
4. Wi-Fi ve mobil veriyle deneyin. Uçak modunda mesaj kuyruğa girmeli;
   yeniden bağlantıda uygulama açıkken yalnız bir sunucu belgesi oluşmalı.
5. Gönderim onayı gelmeden uygulamayı kapatıp açın; giden kutusu korunmalı.
6. Telefonu yeniden başlatın; alınmış mesaj kopyaları ve kimlik korunmalı.
7. Mesajı silin; yalnız gönderen silebilmeli. Alınmış yerel kopyanın geri
   alınmadığına dikkat edin. Daha eski sayfaların canlı dinlenmediğini kontrol edin.
8. Bir hesapla ikinci kurulumu etkinleştirin; eski anahtar gönderimleri
   reddedilmeli, karşı tarafın yeni güvenlik numarası doğrulanmalı.
9. Hesabı silin, silme sırasında bağlantıyı kesin ve uygulamayı yeniden açın;
   yarım kalmış silme akışı tamamlanabilmeli. UID silme işareti kalmalıdır.
10. Büyük yazı, TalkBack, açık/koyu tema, klavye, ekran döndürme ve düşük özellikli
    telefonlarda deneyin. Gönderilmemiş editör metni yalnız bellekte olduğundan
    ekran yeniden oluşturulunca kaybolabilir; şifreli kuyruğa alınan mesaj korunur.

Firebase Console'da mesaj içeriğinin plaintext olarak bulunmadığını; uygulama
loglarında mesaj, şifre veya özel anahtar olmadığını inceleyin. Geniş dağıtımdan
önce bağımsız güvenlik ve bağımlılık/lisans incelemesi yaptırın.
