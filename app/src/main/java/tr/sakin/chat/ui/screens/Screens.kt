@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package tr.sakin.chat.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import tr.sakin.chat.data.ThemeMode
import tr.sakin.chat.domain.*

@Composable private fun Header(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = {
        if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
    }, actions = actions)
}
@Composable private fun Notice(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun Avatar(name: String) {
    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
    }
}
@Composable fun WelcomeScreen(login: () -> Unit, demo: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Outlined.Forum, null, Modifier.padding(24.dp).size(56.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(28.dp))
        Text("SadieText", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text("Sohbete yer aç.", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("Arkadaşlarınla sade bir mesajlaşma deneyimi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Notice("Bu sürüm arayüz önizlemesidir. Hesap açmaz, internete bağlanmaz ve uçtan uca şifreleme içermez. Gerçek veya hassas bilgi girmeyin.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = demo, modifier = Modifier.fillMaxWidth()) { Text("Demoyu keşfet") }
        TextButton(onClick = login, modifier = Modifier.fillMaxWidth()) { Text("Giriş ekranını incele") }
    }
}
@Composable fun AuthScreen(register: Boolean, back: () -> Unit, alternate: () -> Unit, next: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // Password is deliberately not saveable and never passed to the repository.
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Header(if (register) "Hesap oluştur" else "Tekrar merhaba", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sadece sen ve sohbetin.", style = MaterialTheme.typography.headlineSmall)
            Notice("Hesap işlemleri Aşama 2'de Firebase'e bağlanacak. Burada yalnızca formu deneyebilirsiniz.")
            OutlinedTextField(email, { email = it; error = null }, label = { Text("E-posta") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it; error = null }, label = { Text("Şifre (örnek)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() || password.length < 8) {
                    error = "Geçerli bir e-posta ve en az 8 karakterlik örnek şifre girin."
                } else { password = ""; next() }
            }, modifier = Modifier.fillMaxWidth()) { Text("Demo profilini oluştur") }
            if (!register) TextButton(onClick = { dialog = "Şifre sıfırlama Aşama 2'de eklenecek. Bu sürüm e-posta göndermez." }) { Text("Şifremi unuttum") }
            OutlinedButton(onClick = { dialog = "Google ile giriş Aşama 2'de yapılandırılacak." }, modifier = Modifier.fillMaxWidth()) { Text("Google ile giriş (yakında)") }
            TextButton(onClick = alternate) { Text(if (register) "Giriş ekranına dön" else "Kayıt ekranını incele") }
        }
    }
    dialog?.let { text -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Henüz etkin değil") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Tamam") } }) }
}
@Composable fun UsernameScreen(back: () -> Unit, next: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var handle by rememberSaveable { mutableStateOf("") }
    val valid = name.trim().length in 1..40 && Regex("[a-z0-9_]{3,20}").matches(handle)
    Column(Modifier.fillMaxSize()) {
        Header("Seni nasıl tanıyalım?", back)
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Görünen isim") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(handle, { handle = it.take(20) }, label = { Text("Kullanıcı adı") }, prefix = { Text("@") }, singleLine = true,
                supportingText = { Text("3–20 karakter: küçük İngilizce harf, rakam veya _") }, modifier = Modifier.fillMaxWidth())
            Notice("Demo profili yalnızca uygulama açıkken tutulur. Kullanıcı adı benzersizliği henüz sunucudan kontrol edilmez.")
            Button(onClick = { next(name.trim(), handle) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Demoya devam et") }
        }
    }
}
@Composable fun ChatsScreen(name: String, chats: List<Conversation>, search: () -> Unit, settings: () -> Unit, open: (String) -> Unit, games: () -> Unit = {}) {
    Scaffold(topBar = { Header("Sohbetler", actions = {
        RightMenu(settings, games)
    }) }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = search, icon = { Icon(Icons.Outlined.Edit, null) }, text = { Text("Yeni sohbet") })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text("Merhaba, $name", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (chats.isEmpty()) Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("İlk sohbetin burada başlayacak", style = MaterialTheme.typography.titleLarge)
                Text("Yeni sohbet'e dokun ve @deniz veya @ece ara.", Modifier.padding(top = 12.dp))
            } else LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(chats, key = { it.person.id }) { chat ->
                    ListItem(headlineContent = { Text(chat.person.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(chat.messages.lastOrNull()?.text ?: "Bir merhaba yaz · demo", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        leadingContent = { Avatar(chat.person.name) }, trailingContent = { Text(chat.messages.lastOrNull()?.time.orEmpty(), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.animateItem().clickable { open(chat.person.id) })
                }
            }
        }
    }
}
@Composable fun SearchScreen(back: () -> Unit, find: (String) -> Person?, open: (Person) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val person = remember(query) { find(query) }
    Column(Modifier.fillMaxSize()) {
        Header("Yeni sohbet", back)
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(query, { query = it.take(21) }, label = { Text("Tam kullanıcı adı") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Demo kullanıcıları: @deniz ve @ece", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (person != null) ListItem(headlineContent = { Text(person.name) }, supportingContent = { Text("@${person.username} · örnek kişi") },
                leadingContent = { Avatar(person.name) }, modifier = Modifier.clickable { open(person) })
            else if (query.isNotBlank()) Text("Bu kullanıcı bulunamadı.")
        }
    }
}
@Composable fun ChatScreen(chat: Conversation?, back: () -> Unit, send: (String) -> Unit, delete: (String) -> Unit) {
    var text by rememberSaveable(chat?.person?.id) { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val bottom = rememberSmoothMessageScroll(list, chat?.messages?.lastOrNull()?.id, chat?.messages?.size ?: 0, chat?.messages?.lastOrNull()?.mine == true)
    val scrollScope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().imePadding()) {
        Header(chat?.person?.name ?: "Sohbet bulunamadı", back)
        Text("Yerel demo · Mesajlar karşı tarafa ulaşmaz", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (chat == null) { Text("Sohbet bellekte bulunamadı. Geri dönüp yeniden açın.", Modifier.padding(24.dp)); return@Column }
        if (chat.messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("İlk mesajını yaz.") }
        else LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(chat.messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
                    Surface(shape = if(message.mine) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp), color = if(message.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 300.dp).clickable { deleting = message.id }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(message.text)
                            Text("${message.time} · Yerel taslak", Modifier.align(Alignment.End).padding(top = 6.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if(!bottom) TextButton(onClick = { scrollScope.launch { list.animateScrollToItem(chat.messages.lastIndex) } }, modifier = Modifier.align(Alignment.End)) { Text("Son mesaja git ↓") }
        Row(Modifier.fillMaxWidth().animateContentSize().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it.take(2000) }, placeholder = { Text("Mesaj yaz…") }, modifier = Modifier.weight(1f), maxLines = 5, shape = RoundedCornerShape(24.dp))
            FilledIconButton(onClick = { val outgoing = text; text = ""; send(outgoing) }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Demo mesaj ekle")
            }
        }
    }
    deleting?.let { id -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Yerel mesaj silinsin mi?") }, text = { Text("Bu işlem yalnızca bu demodaki mesajı kaldırır.") },
        confirmButton = { TextButton(onClick = { delete(id); deleting = null }) { Text("Sil") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } }) }
}
@Composable fun SettingsScreen(name: String, username: String, theme: ThemeMode, setTheme: (ThemeMode) -> Unit, back: () -> Unit, exit: () -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Header("Profil ve ayarlar", back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(name)
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Text("@$username", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Görünüm", style = MaterialTheme.typography.titleMedium)
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) { ThemeMode.SYSTEM -> "Sistem teması"; ThemeMode.LIGHT -> "Açık tema"; ThemeMode.DARK -> "Koyu tema" }
                Row(Modifier.fillMaxWidth().clickable { setTheme(mode) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = theme == mode, onClick = { setTheme(mode) }); Text(label)
                }
            }
            Notice("Bu çevrimdışı demoda hesap ve gerçek mesaj teslimi yoktur. Örnek mesajlar yalnızca bu uygulama oturumunda tutulur.")
            OutlinedButton(onClick = exit, modifier = Modifier.fillMaxWidth()) { Text("Demodan çık") }
            TextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) { Text("Demo verilerini temizle") }
            Text("Gerçek hesap özellikleri Firebase bağlantılı sürümdedir. Bu demoda e-posta veya şifre girmen gerekmez.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Demo verileri temizlensin mi?") }, text = { Text("Bu oturumdaki profil ve yerel mesajlar silinir.") },
        confirmButton = { TextButton(onClick = { deleting = false; exit() }) { Text("Temizle") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Vazgeç") } })
}
