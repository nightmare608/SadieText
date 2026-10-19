# Durum — 0.2.0-preview

1. **Android ve arayüz:** Proje, Compose/Material 3, Türkçe ekranlar, MVVM,
   Navigation Compose demo akışı, canlı uygulama ekranları ve kalıcı tema tercihi eklendi.
2. **Hesaplar:** Firebase email/şifre kayıt/giriş, doğrulama, şifre sıfırlama,
   oturum, çıkış, yeniden doğrulanan ve kesintiden sonra tamamlanabilen silme eklendi.
   İsteğe bağlı Google/OAuth girişi yapılmadı; Gmail adresi email/şifreyle kullanılabilir.
3. **Kişiler:** Atomik benzersiz kullanıcı adı, kısıtlı tam-ad arama ve
   deterministik iki kişilik sohbet kimliği eklendi.
4. **Taşıma:** Firestore, 50'lik mesaj/sohbet sayfaları, ekran yaşam döngüsüyle
   sınırlanan dinleyiciler, kota/ağ durumları, kuyruk, saat/makbuz/silme ve kurallar eklendi.
5. **Şifreleme:** Gerçek libsignal, Keystore kasası, kalıcı protokol durumu,
   güvenlik numarası, cihaz/kimlik değişimi, ön anahtar yenilemesi ve testler eklendi.
   JVM kriptografi ve emülatör kural testleri çalıştırıldı. Android JNI/Keystore
   cihaz testleri ve bağımsız güvenlik incelemesi hâlâ gereklidir.
6. **Derleme/dağıtım:** Debug ve imzasız release derleme hedefleri, kaynak paket,
   GitHub Actions ve imzalama/Release rehberi hazırlandı. Gerçek sonuçlar
   BUILD_REPORT.md içindedir. Kişisel Firebase projesi bağlanmadı; GitHub'a
   yayın yapılmadı. Yük/enerji testi ve gerçek cihaz performans ölçümü yapılmadı.

Bu önizleme geniş kullanıcı dağıtımı için güvenlik onayı değildir.
Sonraki somut adım: kendi Firebase yapılandırmasını eklemek, iki telefonla
TESTING.md kabul akışını ve Android cihaz testlerini çalıştırmak.
