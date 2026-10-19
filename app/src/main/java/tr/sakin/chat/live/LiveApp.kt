@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package tr.sakin.chat.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import tr.sakin.chat.data.ThemeMode
import kotlinx.coroutines.launch

@Composable fun SetupScreen(demo: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Forum, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("SadieText", style = MaterialTheme.typography.displayMedium)
            Text("Firebase kurulumu gerekiyor", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text("Gerçek hesap ve mesajlaşma kodu hazır. Kendi Firebase projenizin google-services.json dosyasını app klasörüne ekleyip APK'yi yeniden derleyin. Kurulum adımları paketteki README ve FIREBASE belgelerinde.")
            Spacer(Modifier.height(16.dp))
            Text("Bu APK yapılandırılmadığı için sunucuya bağlanmaz. Arayüz demosu gerçek mesaj göndermez.")
            Spacer(Modifier.height(24.dp))
            Button(onClick = demo, modifier = Modifier.fillMaxWidth()) { Text("Arayüz demosunu aç") }
        }
    }
}
@Composable private fun Bar(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = {
        back?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } }
    }, actions = actions)
}
@Composable private fun Info(text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun LiveApp(vm: LiveViewModel, theme: ThemeMode, setTheme: (ThemeMode) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.page, state.chatId, state.listenerRevision, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            when (state.page) {
                LivePage.HOME -> vm.observeHome()
                LivePage.CHAT -> vm.observeChat()
                else -> Unit
            }
        }
    }
    BackHandler(state.page in listOf(LivePage.CHAT, LivePage.SEARCH, LivePage.SETTINGS)) { vm.go(LivePage.HOME) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("ÖNİZLEME · Bağımsız güvenlik denetimi yapılmadı", Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.labelSmall)
            }
            if (!state.online) Info("İnternet bağlantısı yok. Şifreli mesajlar cihazda bekler.")
            state.error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
            if (state.error != null && state.page in listOf(LivePage.HOME, LivePage.CHAT)) {
                TextButton(onClick = vm::retryListeners, enabled = !state.busy) { Text("Yeniden bağlan") }
            }
            state.notice?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary) }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f)) {
                when (state.page) {
                    LivePage.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    LivePage.AUTH -> Auth(vm, state.busy)
                    LivePage.VERIFY -> ActionPage("E-postanı doğrula") {
                        Info("Kayıt adresine gönderilen bağlantıyı aç. Doğrulama tamamlanmadan kullanıcı arama ve mesajlaşma kullanılamaz.")
                        Button(onClick = vm::verifiedEmail, enabled = !state.busy) { Text("Doğruladım, kontrol et") }
                        OutlinedButton(onClick = vm::resend, enabled = !state.busy) { Text("E-postayı yeniden gönder") }
                        TextButton(onClick = vm::signOut, enabled = !state.busy) { Text("Çıkış yap") }
                    }
                    LivePage.PROFILE -> ProfileForm(vm, state.busy)
                    LivePage.DEVICE -> ActionPage("Cihaz anahtarı farklı") {
                        Info("Bu hesap başka bir kurulumda kullanılmış. Bu cihazı etkinleştirirsen eski cihazdan gönderim durdurulur; arkadaşların yeni güvenlik numaranı doğrulamalıdır. Eski mesaj anahtarları geri getirilemez. Tek etkin cihaz desteklenir.")
                        ActivateDevice(vm, state.busy)
                        TextButton(onClick = vm::signOut, enabled = !state.busy) { Text("Çıkış yap") }
                    }
                    LivePage.ERROR -> ActionPage("İşlem tamamlanamadı") {
                        Button(onClick = vm::retry, enabled = !state.busy) { Text("Tekrar dene") }
                        LocalKeyReset(vm, state.busy)
                        TextButton(onClick = vm::signOut, enabled = !state.busy) { Text("Çıkış yap") }
                    }
                    LivePage.DELETE_PENDING -> CompleteDeletion(vm, state.busy)
                    LivePage.HOME -> Home(vm, state)
                    LivePage.SEARCH -> Search(vm, state)
                    LivePage.CHAT -> Chat(vm, state)
                    LivePage.SETTINGS -> Settings(vm, state, theme, setTheme)
                }
            }
        }
    }
}
@Composable private fun ActionPage(title: String, body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall); body()
    }
}
@Composable private fun Auth(vm: LiveViewModel, busy: Boolean) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    ActionPage(if (register) "SadieText'e katıl" else "Tekrar merhaba") {
        Info("Gmail dâhil e-posta adresinle kaydolabilirsin. Bu sürüm e-posta/şifre ile giriş kullanır.")
        OutlinedTextField(email, { email = it }, label = { Text("E-posta") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Şifre") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.login(email, password, register); password = "" }, enabled = !busy && email.isNotBlank() && password.length >= 8, modifier = Modifier.fillMaxWidth()) { Text(if (register) "Kayıt ol" else "Giriş yap") }
        TextButton(onClick = { register = !register; password = "" }, enabled = !busy) { Text(if (register) "Hesabım var" else "Hesap oluştur") }
        TextButton(onClick = { vm.reset(email) }, enabled = !busy && email.isNotBlank()) { Text("Şifremi unuttum") }
    }
}
@Composable private fun ProfileForm(vm: LiveViewModel, busy: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }; var handle by rememberSaveable { mutableStateOf("") }
    ActionPage("Profilini oluştur") {
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Görünen isim") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(handle, { handle = it.take(20) }, label = { Text("Kullanıcı adı") }, prefix = { Text("@") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("3–20 küçük İngilizce harf, rakam veya _. Kullanıcı adı değiştirilemez.")
        Button(onClick = { vm.claim(name, handle) }, enabled = !busy && name.isNotBlank() && Regex("[a-z0-9_]{3,20}").matches(handle)) { Text("Profili kaydet") }
        TextButton(onClick = vm::signOut, enabled = !busy) { Text("Çıkış yap") }
    }
}
@Composable private fun Home(vm: LiveViewModel, state: LiveState) {
    Scaffold(topBar = { Bar("Sohbetler", actions = { IconButton(onClick = { vm.go(LivePage.SETTINGS) }) { Icon(Icons.Outlined.Settings, "Ayarlar") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { vm.go(LivePage.SEARCH) }, text = { Text("Yeni sohbet") }, icon = { Icon(Icons.Outlined.Edit, null) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text("Merhaba, ${state.me?.name.orEmpty()}", Modifier.padding(20.dp))
            if (state.pending > 0) TextButton(onClick = vm::retryOutbox, enabled = !state.busy) { Text("${state.pending} bekleyen mesaj · Yeniden dene") }
            if (state.chats.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(if (state.error != null) "Sohbet listesi yüklenemedi. Yukarıdaki açıklamayı kontrol edip yeniden bağlan."
                else "Henüz sohbet yok. Arkadaşının tam kullanıcı adıyla başlayabilirsin.")
            }
            else LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(state.chats, key = { it.id }) { chat ->
                    ListItem(headlineContent = { Text("@${chat.handle}") }, supportingContent = { Text("Özel sohbet") }, leadingContent = { Icon(Icons.Outlined.PersonOutline, null) }, modifier = Modifier.clickable(enabled = !state.busy) { vm.open(chat.peer) })
                }
                item { if (state.moreChats) TextButton(onClick = vm::olderChats, enabled = !state.busy) { Text("Önceki sohbetleri yükle") } }
            }
        }
    }
}
@Composable private fun Search(vm: LiveViewModel, state: LiveState) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Bar("Kullanıcı bul", { vm.go(LivePage.HOME) })
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(query, { query = it.take(21) }, label = { Text("Tam kullanıcı adı") }, prefix = { Text("@") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.search(query) }, enabled = !state.busy && query.isNotBlank()) { Text("Ara") }
            state.found?.let { person -> ListItem(headlineContent = { Text(person.name) }, supportingContent = { Text("@${person.username}") }, modifier = Modifier.clickable(enabled = !state.busy) { vm.open(person.uid) }) }
            if (state.searched && state.found == null) Text("Kullanıcı bulunamadı.")
        }
    }
}
@Composable private fun Chat(vm: LiveViewModel, state: LiveState) {
    var draft by remember(state.chatId) { mutableStateOf("") }
    var showSafety by remember(state.chatId, state.safety) { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(state.messages.lastOrNull()?.id) { if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.size) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Bar(state.peer?.name ?: "Sohbet", { vm.go(LivePage.HOME) }, actions = { IconButton(onClick = { showSafety = true }) { Icon(Icons.Outlined.VerifiedUser, "Güvenlik numarası") } })
        TextButton(onClick = { showSafety = true }, modifier = Modifier.fillMaxWidth()) { Text(if (state.verified) "Kimlik doğrulandı · Güvenlik numarası" else "Mesajlaşmak için güvenlik numarasını doğrula") }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { if (state.more) TextButton(onClick = vm::older, enabled = !state.busy) { Text("Önceki mesajları yükle") } }
            items(state.messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (message.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 300.dp).clickable(enabled = message.deletable && !state.busy) { deleting = message.id }) {
                        Column(Modifier.padding(14.dp)) { Text(message.text); Text("${message.time} ${message.status}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End).padding(top = 6.dp)) }
                    }
                }
            }
        }
        if (state.pending > 0) TextButton(onClick = vm::retryOutbox, enabled = !state.busy) { Text("Bekleyenleri yeniden gönder") }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, { draft = it.take(2000) }, placeholder = { Text("Mesaj yaz…") }, maxLines = 5, modifier = Modifier.weight(1f))
            FilledIconButton(onClick = { vm.send(draft) { draft = "" } }, enabled = state.verified && !state.busy && draft.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Gönder") }
        }
    }
    if (showSafety) AlertDialog(onDismissRequest = { showSafety = false }, title = { Text("Güvenlik numarası") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Bu numarayı arkadaşının telefonundaki aynı sohbet ekranıyla, yüz yüze veya güvendiğin ayrı bir kanaldan karşılaştır. Bu uygulamanın mesajıyla karşılaştırma.")
            Text(state.safety, style = MaterialTheme.typography.titleMedium)
            Text("Numaralar aynı değilse doğrulama. Yeniden kurulum ve cihaz değişiminde numara değişir.")
        } }, confirmButton = { TextButton(onClick = { vm.verify(); showSafety = false }, enabled = !state.busy) { Text("İki cihazda eşleştirdim") } }, dismissButton = { TextButton(onClick = { showSafety = false }) { Text("Kapat") } })
    deleting?.let { id -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Mesajı sil?") }, text = { Text("Sunucudaki gönderdiğin kopya ve kendi yerel kopyan silinir. Karşı tarafın daha önce aldığı kopya geri alınamaz.") },
        confirmButton = { TextButton(onClick = { vm.deleteMessage(id); deleting = null }) { Text("Sil") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } }) }
}
@Composable private fun Settings(vm: LiveViewModel, state: LiveState, theme: ThemeMode, setTheme: (ThemeMode) -> Unit) {
    var delete by remember { mutableStateOf(false) }; var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Bar("Profil ve ayarlar", { vm.go(LivePage.HOME) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(state.me?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall); Text("@${state.me?.username.orEmpty()}")
            Text("Görünüm", style = MaterialTheme.typography.titleMedium)
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) { ThemeMode.SYSTEM -> "Sistem"; ThemeMode.LIGHT -> "Açık"; ThemeMode.DARK -> "Koyu" }
                Row(Modifier.fillMaxWidth().clickable { setTheme(mode) }, verticalAlignment = Alignment.CenterVertically) { RadioButton(theme == mode, { setTheme(mode) }); Text(label) }
            }
            Info("Şifreleme: Signal libsignal 0.105.1. Bu uygulama Signal değildir; entegrasyonu bağımsız güvenlik denetiminden geçmedi. Bir hesapta tek etkin cihaz desteklenir. Bildirim ve arka planda teslim garantisi yoktur.")
            Info("Çıkış yapınca şifreli yerel geçmiş bu cihazda korunur. Uygulamayı silmek geçmiş anahtarlarını kaybettirir.")
            OutlinedButton(onClick = vm::signOut, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Çıkış yap") }
            TextButton(onClick = { delete = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Hesabımı sil") }
            Text("Açık kaynak · AGPL-3.0 · Tam kaynak proje paketiyle sunulur.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false; password = "" }, title = { Text("Hesabı kalıcı olarak sil") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Gönderdiğin sunucu mesajları, profil, hesap ve yerel anahtarlar silinir. Başkalarının önceden aldığı kopyalar silinemez. İnternet gerekir; işlem yarıda kalırsa tekrar dene.")
            OutlinedTextField(password, { password = it }, label = { Text("Güncel şifren") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        }
    }, confirmButton = { TextButton(onClick = { vm.deleteAccount(password); password = ""; delete = false }, enabled = password.isNotBlank() && !state.busy) { Text("Kalıcı olarak sil") } }, dismissButton = { TextButton(onClick = { delete = false; password = "" }) { Text("Vazgeç") } })
}

@Composable private fun LocalKeyReset(vm: LiveViewModel, busy: Boolean) {
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick = { confirm = true }, enabled = !busy) { Text("Kayıp yerel anahtarları sıfırla") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Yerel geçmiş silinecek") }, text = { Text("Yalnızca cihaz anahtarları kayıpsa kullanın. Bu cihazdaki tüm mesaj kopyaları ve bekleyenler silinir. Yeni anahtarı arkadaşlarınız yeniden doğrulamalı. Bağlantı hatasını düzeltmek için kullanmayın.") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.resetLocalKeys() }) { Text("Geçmişi sil ve sıfırla") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } })
}

@Composable private fun ActivateDevice(vm: LiveViewModel, busy: Boolean) {
    var password by remember { mutableStateOf("") }
    OutlinedTextField(password, { password = it }, label = { Text("Etkinleştirmek için güncel şifren") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
    Button(onClick = { vm.replaceDevice(password); password = "" }, enabled = !busy && password.isNotBlank()) { Text("Bu cihazı etkinleştir") }
}

@Composable private fun CompleteDeletion(vm: LiveViewModel, busy: Boolean) {
    var password by remember { mutableStateOf("") }
    ActionPage("Hesap silmeyi tamamla") {
        Info("Profil silinmiş ancak hesap silme tamamlanmamış. Güncel şifrenle işlemi bitirebilirsin.")
        OutlinedTextField(password, { password = it }, label = { Text("Güncel şifren") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Button(onClick = { vm.deleteAccount(password); password = "" }, enabled = !busy && password.isNotBlank()) { Text("Silme işlemini tamamla") }
        TextButton(onClick = vm::signOut, enabled = !busy) { Text("Çıkış yap") }
    }
}
