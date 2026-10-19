# Güvenlik modeli ve sınırlar — 0.2.0

## Gerçek protokol

Mesaj transportu **org.signal:libsignal-android:0.105.1** kullanır. SessionBuilder,
SessionCipher, imzalı EC ve Kyber anahtar paketleri ve Signal'in ratchet/mesaj
biçimleri kullanılır. Kendi mesaj şifreleme algoritmamız yoktur. Android
Keystore/AES-GCM sadece **yerel dosyayı korur**; mesaj E2EE'sinin yerine geçmez.

Kaynak: https://github.com/signalapp/libsignal/tree/v0.105.1
Lisans: AGPLv3. Signal dışı kullanım upstream tarafından desteklenmez;
API/JNI uyumluluğu güncellemelerde kontrol edilmelidir. Sürüm sabitlenmiştir.
Bu uygulama Signal tarafından onaylanmış veya bağımsız denetlenmiş değildir.

## Kimlik doğrulama

- Firebase oturumuna ek olarak doğrulanmış e-posta gerekir.
- Kullanıcı adı atomik işlemle kullanıcı profiline bağlanır. Profil/handle listelemesi yasaktır.
- İletişimden önce iki kişi 60 haneli Signal güvenlik numarasını ayrı güvenilir
  kanaldan karşılaştırıp onaylar. Karşılaştırmadan düğmeye basmak kimlik güvencesini bozar.
- Tanınmayan veya farklı kimlik sessizce kabul edilmez. Güvenlik numarası yenilenir;
  gönderim durur. Yeni doğrulama eski oturumu temizler.
- Açık kimlik tanımları hem Firestore zarfında hem şifreli gövdede bağlanır.
  Mesaj kimliği, sohbet, gönderen ve alıcı da şifreli gövdede kontrol edilir.
- İlk güven sırasında sunucu anahtar değiştirebilir; iki tarafın bağımsız numara
  karşılaştırması bu nedenle zorunludur. Sunucu erişimi engelleyebilir veya metadata'yı görebilir.

## Anahtar ve yerel veri

Signal özel anahtarları, ratchet kayıtları, güvenilen kimlikler, giden kutusu ve
alınan/açılmış mesaj önbelleği, Keystore'daki dışarı verilmeyen AES anahtarıyla
şifrelenmiş AtomicFile içinde `noBackupFilesDir` altında tutulur. Her yazmada
Android sağlayıcısının yeni GCM IV'si kullanılır; hesap uid'si AAD'ye bağlanır.
Yedekleme kapalıdır. Mesaj veya özel anahtar loglama yapılmaz.

Protokol durumu ile giden şifreli zarf **aynı yerel atomik kayıtta** saklanır.
Alınan mesajın çözülen kopyası da ratchet güncellemesiyle birlikte kaydedilir.
Yerel kayıt başarısızsa durum geri alınır. Ağ tekrarında şifreleme yeniden
çalıştırılmaz; aynı UUID ve ciphertext kullanılır. Firestore işlemi aynı UUID'yi
yeniden üretmez. Önceden teslim edilmiş mesajlar yerel doğrulanmış önbellekten okunur.

Tam kaynak/bağlam eşleşmesi olmadan bir sohbetin önbelleği diğerine taşınamaz.
Şifre, gönderilmemiş editör metni ve çözülen içerik uygulama belleğinde bulunur;
bunlar hata raporuna veya Android kaydedilmiş ekran durumuna yazılmaz.
Bellek ve açık cihaz, kök erişimi veya zararlı işletim sistemi karşısında güvenli
kabul edilmez. Yerel kasa biyometrik kilit gerektirmez.

## Ön anahtar politikası

İmzalı EC ve last-resort Kyber anahtarları uygulama oturumu yeniden kurulurken
yaşı en az 7 günse yenilenir. Son 8 nesil gecikmiş ilk temaslar için korunur.
Daha eski ilk temas mesajı açılamayabilir. Kimlik anahtarı bu yenilemede değişmez.
Last-resort Kyber kullanımında `(kyber id, signed id, base key)` tekrarları
kalıcı biçimde takip edilip reddedilir. Tek kullanımlık EC ön anahtar havuzu bu
sürümde yoktur; libsignal'ın desteklediği ön anahtarsız/last-resort yolu kullanılır.
Bu, Signal uygulamasının tüm işletim politikalarının kopyası değildir.

## Yeniden kurulum ve cihaz değişimi

Tek etkin cihaz desteklenir. Yeni kurulum yeni kimlik üretir ve önceki cihazla
aynı anahtar olmadığı için açık cihaz etkinleştirme onayı ve yeniden şifre girişi
ister. Sunucuda kimlik değişimi için yakın zamanda giriş yapılmış olması zorlanır.
Eski cihaz çevrimiçiyken anahtar eşleşmesi kontrolünde durur; eski anahtar kimliğiyle
gönderim sunucuda reddedilir. Alıcı yeni kimliği ayrıca doğrulamalıdır.

Yeni cihaz geçmişteki anahtarları veya mesaj kopyalarını geri alamaz. Yedek,
çoklu cihaz ve geçmiş aktarımı yoktur. Keystore kaybında otomatik olarak zayıf
şifrelemeye veya sessiz anahtar değişimine geçilmez. Hata ekranındaki açık onaylı
sıfırlama yerel geçmişi ve kuyruğu siler, yeni cihaz/kimlik akışına geçer.

## Sunucu kuralları

2026-10-18 yeniden incelemesi: var olmayan sohbet get istisnası kaldırıldı;
yeni sohbet ID'si `p2:uidA:uidB` biçiminde sıralı üyelere bağlandı. Kurallarda
hashing kullanılmaz. Yeni APK mevcut sohbeti üyelik sorgusuyla bulur;
yalnız yeni belgeyi oluşturmadan önce doğrudan okumaz. Ayrıntılar ve kalan sınırlar: [FIRESTORE_RULES.md](FIRESTORE_RULES.md).

İki katılımcı dışındaki kullanıcılar sohbeti/mesajları okuyamaz veya yazamaz.
Katılımcılar değiştirilemez. Gönderen taklit edilemez; okunma makbuzunu yalnız
alıcı, bir kez ve sunucu saatiyle yazabilir. Düz metin alanı ve sınırsız sorgular
reddedilir. Alanların şifreli olması sunucu kurallarının yerine geçmez.

Kurallar libsignal ciphertext yapısını veya plaintext yokluğunu kendiliğinden
kanıtlayamaz: kötü niyetli bir istemci ciphertext alanına rastgele metin yazabilir.
Resmi istemci yalnız libsignal çıktısı yollar; alıcı bunu kriptografik olarak doğrular.

## Silme ve gizlilik sınırları

Gönderen kendi sunucu ciphertext'ini ve yerel kopyasını silebilir. Alıcının daha
önce sakladığı kopya geri alınamaz. Eski sayfalardaki silmeler o sayfa yeniden
alınana kadar görünmeyebilir; yalnız en yeni 50 mesaja canlı dinleyici bağlıdır.

Hesap silme güncel şifreyi doğrular; tüm sayfalardaki gönderilmiş sunucu mesajları,
handle ve profil temizlenir; Auth hesabı ve yerel kasa silinir. İşlem birden fazla
hizmet içerdiğinden tek atomik işlem değildir; bağlantı kesilirse tekrar denenir.
Silme işareti uid/zaman içerir ve eski jetonla yeniden profil oluşturmayı engellemek
üzere tutulur. Diğer katılımcıların sohbet belgeleri ve kendi mesajları korunur.

Firebase e-postayı/oturumu; Firestore katılımcıları, saatleri ve mesaj boyutlarını
görebilir. Trafik gizleme, anonimlik veya engellemelere karşı erişim garantisi yoktur.

## Dağıtım öncesi kalanlar

Gerçek iki telefon, Android Keystore/JNI cihaz testleri, düşük özellikli cihaz
performansı ve bağımsız güvenlik incelemesi gerekir. Kötüye kullanım/hız sınırlaması,
engelleme ve şikâyet özellikleri eksiktir. Güvenli üretim hizmeti olduğu iddia edilmez.
