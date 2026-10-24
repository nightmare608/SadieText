@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package tr.sakin.chat.games.phone

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val Ink = Color(0xFF101827)
private val Mint = Color(0xFF91E9C0)
private val Panel = Color(0xFF1B2638)
private val Soft = Color(0xFFA8B7CB)
private val GameColors = darkColorScheme(primary = Mint, onPrimary = Ink, secondary = Color(0xFFB8AFF8), surface = Ink,
    surfaceContainer = Panel, surfaceVariant = Panel, background = Ink, onSurface = Color(0xFFF1F5FA), onSurfaceVariant = Soft)

@Composable fun GamesScreen(back: () -> Unit, phone: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Oyunlar") }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Geri") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sohbete küçük bir mola.", style = MaterialTheme.typography.headlineSmall)
            Card(onClick = phone, colors = CardDefaults.cardColors(containerColor = Ink)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(40.dp), tint = Mint)
                    Text("Telefon Şirketim", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Kendi markanı kur. Telefon tasarla, satışa çıkar ve teknoloji dünyasında büyü.", color = Soft)
                    Text("${ChipCatalog.all.size} işlemci · Çevrimdışı · Otomatik kayıt", color = Mint, style = MaterialTheme.typography.labelLarge)
                    Button(onClick = phone, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink)) { Text("Şirketine git") }
                }
            }
            Text("İlerleme bu cihazda, kullandığın SadieText hesabına ayrı kaydedilir. Uygulamayı silmek oyun kaydını da siler.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable fun PhoneCompanyScreen(scope: String, back: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: PhoneViewModel = viewModel(key = "phone-$scope", factory = remember(scope) { PhoneViewModel.Factory(app, scope) })
    val ui by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable(scope) { mutableStateOf("Şirket") }
    var reset by remember { mutableStateOf(false) }
    BackHandler { if(!ui.saving) { if(tab != "Şirket") tab = "Şirket" else back() } }
    MaterialTheme(colorScheme = GameColors) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(title = { Column { Text("Telefon Şirketim", style = MaterialTheme.typography.titleLarge); Text(ui.company?.name?.ifBlank { "SadieText Oyunlar" } ?: "SadieText Oyunlar", style = MaterialTheme.typography.labelSmall, color = Soft) } },
                    navigationIcon = { IconButton(onClick = back, enabled = !ui.saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Oyunlara dön") } },
                    actions = { IconButton(onClick = { tab = "Rehber" }) { Icon(Icons.AutoMirrored.Outlined.HelpOutline,"Oyun rehberi") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink))
                if(ui.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if(ui.company == null || ui.company!!.name.isBlank()) {
                    FoundCompany(vm, ui.saving)
                } else {
                    val s = ui.company!!
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(PhoneEngine.money(s.cash), style = MaterialTheme.typography.headlineSmall, color = if(s.cash < 0) MaterialTheme.colorScheme.error else Mint, fontWeight = FontWeight.Bold)
                        Text("HAFTA ${s.week} · İTİBAR %${s.reputation}", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelSmall, color = Soft)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Şirket","Tasarım","Modeller","Ar-Ge","Finans","Pazar","Yorumlar","Rehber").forEach { name -> FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) }) }
                    }
                    if(ui.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Box(Modifier.weight(1f)) {
                        when(tab) {
                            "Şirket" -> Dashboard(s, ui.saving, { tab = "Tasarım" }, { vm.change(PhoneEngine::nextWeek) })
                            "Tasarım" -> Designer(s, ui.saving, { tab = "Modeller" }) { d, count, price -> vm.change { PhoneEngine.launch(it,d,count,price) } }
                            "Modeller" -> Products(s,ui.saving,vm)
                            "Ar-Ge" -> Research(s,ui.saving,vm)
                            "Finans" -> Finance(s,ui.saving,vm)
                            "Pazar" -> Market(s,ui.saving,vm)
                            "Yorumlar" -> Reviews(s)
                            else -> Guide { reset = true }
                        }
                    }
                }
            }
            ui.error?.let { error -> AlertDialog(onDismissRequest = vm::clearError, title = { Text("İşlem tamamlanamadı") }, text = { Text(error) }, confirmButton = { TextButton(onClick = vm::clearError) { Text("Tamam") } }) }
            if(reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Yeni şirket kurulsun mu?") }, text = { Text("Bu hesaptaki mevcut şirket ve ilerleme silinir. Sohbetlerin etkilenmez.") },
                confirmButton = { TextButton(onClick = { vm.newCompany("Sadie Mobile"); reset = false; tab = "Şirket" }) { Text("Yeni şirket kur") } }, dismissButton = { TextButton(onClick = { reset = false }) { Text("Vazgeç") } })
        }
    }
}
@Composable private fun FoundCompany(vm: PhoneViewModel, busy: Boolean) {
    var name by rememberSaveable { mutableStateOf("Sadie Mobile") }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Bir fikirden,\ndünya markasına.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold) }
        item { PhoneArt(Blueprint(), Modifier.fillMaxWidth().height(240.dp)) }
        item { Text("₺500.000 başlangıç sermayesi. İlk telefonunu tasarla, stok üret ve her hafta satış sonuçlarını gör.", color = Soft) }
        item { OutlinedTextField(name,{ name = it.take(30) },label = { Text("Şirketinin adı") },singleLine = true,modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { vm.newCompany(name) }, enabled = !busy && name.trim().length >= 2, modifier = Modifier.fillMaxWidth()) { Text("Şirketimi kur") } }
    }
}
@Composable private fun GameCard(title: String, body: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); body() }
    }
}
@Composable private fun Stat(label: String, value: String, color: Color = Mint) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = Soft); Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold) }
}
@Composable private fun Dashboard(s: Company, busy: Boolean, design: () -> Unit, week: () -> Unit) {
    val last = s.reports.lastOrNull()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { GameCard("Sıradaki büyük fikrin") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Stat("Toplam satış", "${s.products.sumOf { it.sold - it.returned }} adet"); Stat("Aktif model", "${s.products.count { it.active }}") }
            Text(s.lastNews, color = Soft)
            Button(onClick = design, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add,null); Spacer(Modifier.width(8.dp)); Text("Yeni telefon tasarla") }
        } }
        item { GameCard("Bu haftayı yönet") {
            Text("Satışlar yalnızca haftayı ilerlettiğinde hesaplanır. Kapalıyken gelir veya gider oluşmaz.", color = Soft)
            Text("İşletme: ${PhoneEngine.money(2500L+s.staff*1600+s.factory*900)} · Reklam: ${PhoneEngine.money(s.marketing*6500L)} · Faiz: ${PhoneEngine.money(s.debt/100)}", style = MaterialTheme.typography.bodySmall)
            Button(onClick = week, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Haftayı ilerlet →") }
        } }
        if(last != null) item { GameCard("${last.week}. hafta özeti") {
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) { Stat("Ciro", PhoneEngine.money(last.revenue)); Stat("Kâr / zarar",PhoneEngine.money(last.profit),if(last.profit >= 0) Mint else Color(0xFFFFA3A3)) }
            Text("${last.sold} satış · ${s.products.sumOf { it.inventory }} adet stok", color = Soft)
            Sparkline(s.reports)
        } }
        item { GameCard("Hedefler") {
            listOf("İlk modelini satışa çıkar" to s.products.isNotEmpty(), "100 telefon sat" to (s.products.sumOf { it.sold } >= 100), "İtibarını %70'e çıkar" to (s.reputation >= 70), "En ileri Ar-Ge seviyesine ulaş" to (s.research == 3), "₺1.000.000 nakit biriktir" to (s.cash >= 1_000_000)).forEach { (label,done) -> Text("${if(done) "✓" else "○"}  $label",color = if(done) Mint else Soft) }
        } }
    }
}
@Composable private fun <T> Picker(label: String, value: T, options: List<T>, name: (T) -> String, enabled: (T) -> Boolean = { true }, select: (T) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedCard(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelSmall, color = Soft); Text(name(value),style = MaterialTheme.typography.bodyMedium) }; Icon(Icons.Outlined.ExpandMore,null)
        }
    }
    if(show) AlertDialog(onDismissRequest = { show = false }, title = { Text(label) }, text = {
        LazyColumn(Modifier.heightIn(max = 400.dp)) { items(options) { option -> ListItem(headlineContent = { Text(name(option)) }, supportingContent = { if(!enabled(option)) Text("Ar-Ge seviyesi yetersiz") }, trailingContent = { if(option == value) Icon(Icons.Outlined.Check,null) }, modifier = Modifier.clickable(enabled = enabled(option)) { select(option); show = false }) } }
    }, confirmButton = { TextButton(onClick = { show = false }) { Text("Kapat") } })
}
@Composable private fun Designer(s: Company, busy: Boolean, onLaunched: () -> Unit, launch: (Blueprint,Int,Long) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Model ${s.nextId}") }; var chip by rememberSaveable { mutableStateOf(ChipCatalog.defaultId) }
    var ram by rememberSaveable { mutableIntStateOf(6) }; var storage by rememberSaveable { mutableIntStateOf(128) }
    var screen by rememberSaveable { mutableIntStateOf(2) }; var camera by rememberSaveable { mutableIntStateOf(2) }
    var battery by rememberSaveable { mutableIntStateOf(5000) }; var body by rememberSaveable { mutableIntStateOf(0) }
    var cooling by rememberSaveable { mutableIntStateOf(0) }; var support by rememberSaveable { mutableIntStateOf(2) }; var color by rememberSaveable { mutableIntStateOf(0) }
    var audience by rememberSaveable { mutableStateOf(Audience.VALUE.name) }; var count by rememberSaveable { mutableStateOf("40") }; var price by rememberSaveable { mutableStateOf("5990") }
    val startId = remember { s.nextId }
    LaunchedEffect(s.nextId) { if(s.nextId > startId) onLaunched() }
    var chips by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }
    val d = Blueprint(name,chip,ram,storage,screen,camera,battery,body,cooling,support,color,Audience.valueOf(audience))
    val unit = PhoneEngine.cost(d,s); val development = PhoneEngine.development(d); val batch = count.toIntOrNull() ?: 0; val retail = price.toLongOrNull() ?: 0
    LazyColumn(modifier = Modifier.testTag("phone-designer-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PhoneArt(d,Modifier.fillMaxWidth().height(230.dp)) }
        item { OutlinedTextField(name,{ name = it.take(30) }, label = { Text("Model adı") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Picker("Hedef kitle",d.audience,Audience.entries.toList(),{ it.label }) { audience = it.name } }
        item { OutlinedCard(onClick = { chips = true },modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("İşlemci · ${ChipCatalog.all.size} seçenek",color = Soft,style = MaterialTheme.typography.labelSmall); Text(ChipCatalog.get(chip).name); Text("İşlemci kataloğunu aç →",color = Mint) } } }
        item { Picker("RAM",ram,Parts.ram,{ "$it GB" },{ it <= listOf(6,8,16,24)[s.research] }) { ram = it } }
        item { Picker("Depolama",storage,Parts.storage,{ "$it GB" },{ it <= listOf(128,256,512,1024)[s.research] }) { storage = it } }
        item { Picker("Ekran",screen,Parts.screens.indices.toList(),{ Parts.screens[it].label },{ it <= s.research+1 }) { screen = it } }
        item { Picker("Kamera",camera,Parts.cameras.indices.toList(),{ Parts.cameras[it].label },{ it <= s.research+1 }) { camera = it } }
        item { Picker("Pil",battery,Parts.batteries,{ "$it mAh" }) { battery = it } }
        item { Picker("Kasa",body,Parts.bodies.indices.toList(),{ Parts.bodies[it].label }) { body = it } }
        item { Picker("Soğutma",cooling,Parts.cooling.indices.toList(),{ Parts.cooling[it].label }) { cooling = it } }
        item { Picker("Güncelleme desteği",support,(1..5).toList(),{ "$it yıl · oyun ölçeği" }) { support = it } }
        item { Picker("Renk",color,Parts.colors.indices.toList(),{ Parts.colors[it] }) { color = it } }
        item { GameCard("Prototip değerlendirmesi") {
            PhoneEngine.qualities(d,s).zip(listOf("Performans","Kamera","Pil","Tasarım")).forEach { (score,label) -> Score(label,score) }
            Text("Hedef kitle uyumu: ${PhoneEngine.quality(d,s)}/100", color = Mint)
            Text("Puanlar ve maliyetler oyun dengesidir; gerçek benchmark veya tedarik fiyatı değildir.",style = MaterialTheme.typography.bodySmall,color = Soft)
        } }
        item { GameCard("Üretim ve lansman") {
            OutlinedTextField(count,{ count = it.filter(Char::isDigit).take(5) }, label = { Text("İlk parti adedi · 10–${PhoneEngine.capacity(s)}") },keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),singleLine = true,modifier = Modifier.fillMaxWidth())
            OutlinedTextField(price,{ price = it.filter(Char::isDigit).take(6) }, label = { Text("Satış fiyatı · ₺") },keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),singleLine = true,modifier = Modifier.fillMaxWidth())
            Text("Birim maliyet: ${PhoneEngine.money(unit)}\nGeliştirme: ${PhoneEngine.money(development)}\nBaşlangıç harcaması: ${PhoneEngine.money(unit*batch+development)}\nBirim brüt marj: ${PhoneEngine.money(retail-unit)}",color = Soft)
            if(retail < unit) Text("Bu fiyatla her satışta zarar edersin.",color = MaterialTheme.colorScheme.error)
            Button(onClick = { confirm = true }, enabled = !busy && batch in 10..PhoneEngine.capacity(s) && retail in 100..100_000 && name.isNotBlank(),modifier = Modifier.fillMaxWidth()) { Text("Lansmanı hazırla") }
        } }
    }
    if(chips) ChipPicker(s.research, chip, { chip = it; chips = false }, { chips = false })
    if(confirm) AlertDialog(onDismissRequest = { confirm = false },title = { Text("$name satışa çıksın mı?") },text = { Text("$batch adet üretim ve geliştirme için ${PhoneEngine.money(unit*batch+development)} harcanacak. Satış fiyatı ${PhoneEngine.money(retail)}. Satışlar haftayı ilerlettiğinde başlar.") },
        confirmButton = { TextButton(onClick = { launch(d,batch,retail); confirm = false }) { Text("Üret ve satışa çıkar") } },dismissButton = { TextButton(onClick = { confirm = false }) { Text("Vazgeç") } })
}
@Composable private fun ChipPicker(level: Int, selected: String, choose: (String) -> Unit, dismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }; var maker by rememberSaveable { mutableStateOf("Tümü") }
    val makers = remember { listOf("Tümü") + ChipCatalog.all.map { it.maker }.distinct() }
    val filtered = remember(query,maker) { ChipCatalog.all.filter { (maker == "Tümü" || it.maker == maker) && it.name.contains(query,true) }.sortedByDescending { it.power } }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(max = 650.dp)) {
            Text("İşlemci kütüphanesi",style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(query,{ query = it.take(60) },placeholder = { Text("İşlemci ara…") },leadingIcon = { Icon(Icons.Outlined.Search,null) },singleLine = true,modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) { makers.forEach { brand -> FilterChip(selected = maker == brand,onClick = { maker = brand },label = { Text(brand) }) } }
            Text("${filtered.size} sonuç · Kurgu puanlar / oyun maliyeti",style = MaterialTheme.typography.labelSmall,color = Soft)
            LazyColumn(Modifier.weight(1f,fill = false)) { items(filtered,key = { it.id }) { chip ->
                ListItem(headlineContent = { Text(chip.name) },supportingContent = { Text("${chip.maker} · Güç ${chip.power} · Verim ${chip.efficiency}${if(chip.tier > level) " · Ar-Ge ${chip.tier} gerekli" else ""}") },
                    trailingContent = { if(chip.id == selected) Icon(Icons.Outlined.Check,null) else if(chip.tier > level) Icon(Icons.Outlined.Lock,null) },modifier = Modifier.clickable(enabled = chip.tier <= level) { choose(chip.id) })
            } }
        }
    }
}
@Composable private fun Products(s: Company, busy: Boolean, vm: PhoneViewModel) {
    LazyColumn(contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if(s.products.isEmpty()) item { GameCard("Vitrinin henüz boş") { Text("Tasarım sekmesinden ilk modelini oluştur.",color = Soft) } }
        items(s.products.reversed(),key = { it.id }) { p -> ProductCard(p,s,busy,vm) }
    }
}
@Composable private fun ProductCard(p: Phone, s: Company, busy: Boolean, vm: PhoneViewModel) {
    var price by rememberSaveable(p.id,p.price) { mutableStateOf(p.price.toString()) }; var batch by rememberSaveable(p.id) { mutableStateOf("50") }
    GameCard(p.design.name) {
        PhoneArt(p.design,Modifier.fillMaxWidth().height(150.dp))
        Text("${ChipCatalog.get(p.design.chipId).name} · ${p.design.ram}/${p.design.storage} GB",color = Soft)
        Text("Stok ${p.inventory} · Satış ${p.sold} · İade ${p.returned}",fontWeight = FontWeight.SemiBold)
        Text("Birim maliyet ${PhoneEngine.money(p.unitCost)} · ${if(p.active) "Satışta" else "Satış durduruldu"}",color = Soft)
        OutlinedTextField(price,{ price = it.filter(Char::isDigit).take(6) },label = { Text("Yeni fiyat · ₺") },singleLine = true,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { vm.change { PhoneEngine.price(it,p.id,price.toLongOrNull() ?: 0) } },enabled = !busy) { Text("Fiyatı güncelle") }
        OutlinedTextField(batch,{ batch = it.filter(Char::isDigit).take(5) },label = { Text("Ek üretim · en fazla ${PhoneEngine.capacity(s)}") },singleLine = true,keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),modifier = Modifier.fillMaxWidth())
        Text("Üretim tutarı: ${PhoneEngine.money((batch.toIntOrNull() ?: 0)*p.unitCost)}",color = Soft,style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.change { PhoneEngine.produce(it,p.id,batch.toIntOrNull() ?: 0) } },enabled = !busy && p.active) { Text("Stok üret") }
        TextButton(onClick = { vm.change { PhoneEngine.toggle(it,p.id) } },enabled = !busy) { Text(if(p.active) "Satışı durdur" else "Satışı aç") }
    }
}
@Composable private fun Research(s: Company, busy: Boolean, vm: PhoneViewModel) {
    val upgrades = listOf(Triple("research","Ar-Ge",s.research),Triple("factory","Fabrika",s.factory),Triple("quality","Kalite kontrol",s.quality),Triple("staff","Ekip",s.staff))
    LazyColumn(contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Geleceğe yatırım yap.",style = MaterialTheme.typography.headlineSmall,fontWeight = FontWeight.Bold) }
        items(upgrades) { (type,title,level) -> GameCard("$title · Seviye $level") {
            Text(when(type) { "research" -> "Yeni işlemciler, daha fazla RAM, depolama, ekran ve kamera açar. Kamera yazılımının puanını geliştirir."; "factory" -> "Parti kapasitesi ${PhoneEngine.capacity(s)} adet. Yeni modellerde parça maliyetini azaltır; mevcut stokların maliyeti değişmez."; "quality" -> "Arıza / iade riskini azaltır, müşteri memnuniyetini artırır."; else -> "Satış ekibini genişletir. Talep artar; haftalık maaş gideri de yükselir." },color = Soft)
            val maxed = level >= if(type == "research") 3 else 5
            Button(onClick = { vm.change { PhoneEngine.upgrade(it,type) } },enabled = !busy && !maxed) { Text(if(maxed) "En yüksek seviye" else "Geliştir · ${PhoneEngine.money(PhoneEngine.upgradeCost(s,type))}") }
        } }
    }
}
@Composable private fun Finance(s: Company, busy: Boolean, vm: PhoneViewModel) {
    var confirmLoan by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { GameCard("Bilanço") {
            Stat("Nakit",PhoneEngine.money(s.cash)); Stat("Stok değeri",PhoneEngine.money(s.products.sumOf { it.inventory * it.unitCost })); Stat("Kredi borcu",PhoneEngine.money(s.debt),Color(0xFFB8AFF8))
            Text("Özkaynak: ${PhoneEngine.money(s.cash+s.products.sumOf { it.inventory*it.unitCost }-s.debt)}",color = Soft)
            Text("Üretim nakit çıkışıdır; satılana kadar stok varlığıdır. Satılan / iade edilen ürünün maliyeti kârdan düşülür. Yatırımlar oyun ölçeğinde doğrudan giderleşir.",style = MaterialTheme.typography.bodySmall,color = Soft)
        } }
        item { GameCard("Finansman") {
            Text("₺100.000 dilimler · ₺400.000 limit · Her oyun haftası %1 faiz. Bunlar oyun kurallarıdır.",color = Soft)
            Button(onClick = { confirmLoan = true },enabled = !busy && s.debt < 400_000) { Text("₺100.000 kredi al") }
            OutlinedButton(onClick = { vm.change(PhoneEngine::repay) },enabled = !busy && s.debt > 0) { Text("₺100.000'e kadar borç öde") }
        } }
        if(s.reports.isNotEmpty()) item { GameCard("Haftalık kâr / zarar") {
            Sparkline(s.reports)
            s.reports.takeLast(8).reversed().forEach { Text("${it.week}. hafta · Ciro ${PhoneEngine.money(it.revenue)} · Sonuç ${PhoneEngine.money(it.profit)}",style = MaterialTheme.typography.bodySmall,color = if(it.profit >= 0) Mint else Color(0xFFFFA3A3)) }
        } }
        item { Text("Nakit hareketleri",style = MaterialTheme.typography.titleLarge) }
        items(s.ledger.takeLast(60).reversed()) { e -> ListItem(headlineContent = { Text(e.title) },supportingContent = { Text("${e.week}. hafta · Kâr etkisi ${PhoneEngine.money(e.profit)}") },trailingContent = { Text(PhoneEngine.money(e.cash),color = if(e.cash >= 0) Mint else Color(0xFFFFA3A3)) }) }
    }
    if(confirmLoan) AlertDialog(onDismissRequest = { confirmLoan = false },title = { Text("Oyun kredisi al") },text = { Text("Nakit ₺100.000 artar. Kredi borcu kâr sayılmaz; her hafta %1 faiz ödersin.") },confirmButton = { TextButton(onClick = { vm.change(PhoneEngine::borrow); confirmLoan = false }) { Text("Kredi al") } },dismissButton = { TextButton(onClick = { confirmLoan = false }) { Text("Vazgeç") } })
}
@Composable private fun Market(s: Company, busy: Boolean, vm: PhoneViewModel) {
    LazyColumn(contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { GameCard("Pazarın nabzı") { Text(s.lastNews,color = Soft); Text("Aynı hedef kitledeki kendi modellerin de talebi paylaşır. Eski modeller zamanla daha az ilgi görür.",style = MaterialTheme.typography.bodySmall,color = Soft) } }
        item { GameCard("Haftalık reklam bütçesi") {
            (0..3).forEach { level -> Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { vm.change { PhoneEngine.marketing(it,level) } },verticalAlignment = Alignment.CenterVertically) { RadioButton(s.marketing == level,{ vm.change { PhoneEngine.marketing(it,level) } },enabled = !busy); Column { Text(listOf("Organik büyüme","Sosyal medya","İçerik üreticileri","Büyük lansman")[level]); Text("${PhoneEngine.money(level*6500L)} / hafta",style = MaterialTheme.typography.labelSmall,color = Soft) } } }
            Text("Bütçe seçimi hemen para çekmez; haftayı ilerlettiğinde gider yazılır.",style = MaterialTheme.typography.bodySmall,color = Soft)
        } }
        items(Audience.entries.toList()) { a -> GameCard(a.label) { Text(when(a) { Audience.VALUE -> "Pil, dengeli performans ve uygun fiyat. Gereksiz pahalı parçalar marjını azaltabilir."; Audience.GAMING -> "Güçlü işlemci, RAM ve soğutma. Yüksek güçte grafit soğutma iade riskini artırır."; Audience.CAMERA -> "İyi sensör, OIS ve kamera yazılımı. Ar-Ge kamerayı daha iyi değerlendirir."; Audience.PREMIUM -> "Ekran, kasa kalitesi ve güncelleme desteği. Ucuz görünen bir model bu kitleyi zor ikna eder." },color = Soft) } }
    }
}
@Composable private fun Reviews(s: Company) {
    LazyColumn(contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Müşteriler ne düşünüyor?",style = MaterialTheme.typography.headlineSmall) }
        if(s.products.none { it.sold > 0 }) item { Text("İlk satışlardan sonra yorumlar burada görünür. Şirket sekmesinde haftayı ilerlet.",color = Soft) }
        items(s.products.filter { it.sold > 0 }.reversed(),key = { it.id }) { p -> GameCard(p.design.name) {
            Score("Beğenilme",p.likes); Text("“${p.review}”",style = MaterialTheme.typography.titleMedium)
            Text("İade: ${p.returned} / ${p.sold} · Yorumlar simülasyon tarafından üretilir.",color = Soft,style = MaterialTheme.typography.bodySmall)
        } }
    }
}
@Composable private fun Guide(reset: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { GameCard("İlk telefonunu satışa çıkar") {
            Text("1. Tasarım'da hedef kitleni ve parçalarını seç.\n2. Başlangıçta 20–50 adetlik küçük parti dene; giderler için nakit bırak.\n3. Birim maliyetin üstünde, müşterinin ödeyebileceği bir fiyat belirle.\n4. Şirket'te haftayı ilerlet.\n5. Finans ve Yorumlar'a bak; stok üret, fiyatı ayarla, Ar-Ge'ye yatırım yap.",color = Soft)
        } }
        item { GameCard("Oyun kuralları") {
            Text("Oyun çevrimdışı ve tek oyunculudur. Gerçek para, satın alma, çevrimdışı gelir veya çevrimiçi sıralama yoktur. Haftalar yalnızca sen ilerletince geçer. Nakit eksiye düşerse kredi, fiyat ve stoklarla toparlanabilir ya da yeni şirket kurabilirsin.",color = Soft)
            Text("${ChipCatalog.all.size} isimlik geniş bir mobil / tarihî işlemci kataloğu içerir; dünyadaki bütün işlemcilerin eksiksiz arşivi değildir. Puanlar, maliyetler, tedarik erişimi ve Apple / Tensor / Kirin gibi kapalı platformları kullanabilmek oyun kurgusudur. Markalar ilgili sahiplerine aittir; iş birliği iddiası yoktur.",color = Soft)
            Text("Otomatik kayıt bu cihazdaki mevcut SadieText hesabına ayrıdır. Uygulama silinirse veya verileri temizlenirse kayıt kaybolur. Sohbet verileri oyun tarafından okunmaz.",color = Soft)
        } }
        item { OutlinedButton(onClick = reset,modifier = Modifier.fillMaxWidth()) { Text("Yeni şirket kur / oyunu sıfırla") } }
    }
}
@Composable private fun Score(label: String, value: Int) {
    val progress by animateFloatAsState(value / 100f,label = "puan")
    Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) { Text(label,color = Soft); Text("$value/100",color = Mint) }
    LinearProgressIndicator(progress = { progress },modifier = Modifier.fillMaxWidth(),color = Mint,trackColor = Ink)
}
@Composable private fun Sparkline(reports: List<WeekReport>) {
    val values = reports.takeLast(24).map { it.profit.toFloat() }
    Canvas(Modifier.fillMaxWidth().height(88.dp)) {
        if(values.isNotEmpty()) {
            val lo = minOf(0f,values.min()); val hi = maxOf(1f,values.max()); val range = (hi-lo).coerceAtLeast(1f)
            val zero = size.height * (hi / range)
            drawLine(Soft.copy(alpha = .25f),Offset(0f,zero),Offset(size.width,zero),1.dp.toPx())
            val w = size.width / values.size
            values.forEachIndexed { i,v -> val y = size.height*((hi-v)/range); drawRoundRect(if(v >= 0) Mint else Color(0xFFFFA3A3),Offset(i*w+2,minOf(y,zero)),Size((w-4).coerceAtLeast(1f),kotlin.math.abs(y-zero).coerceAtLeast(2f)),CornerRadius(3.dp.toPx())) }
        }
    }
}
private val PhonePalette = listOf(Color(0xFF617DAB),Color(0xFFB4A3DF),Color(0xFFDEE8EC),Color(0xFFE99480),Color(0xFF8FB5A3))

@Composable
private fun PhoneArt(d: Blueprint, modifier: Modifier) {
    val color by animateColorAsState(PhonePalette[d.color],label = "telefon rengi")
    Canvas(modifier.background(Brush.radialGradient(listOf(Color(0xFF293E51),Ink)),RoundedCornerShape(24.dp))) {
        val h = size.height*.84f; val w = h*.49f; val x = size.width*.5f-w*.87f; val y = size.height*.07f
        drawRoundRect(Color.Black.copy(alpha = .3f),Offset(x+12,y+12),Size(w*1.8f,h),CornerRadius(22.dp.toPx()))
        drawRoundRect(Brush.linearGradient(listOf(color,color.copy(alpha = .65f))),Offset(x,y),Size(w,h),CornerRadius(20.dp.toPx()))
        drawRoundRect(Color.White.copy(alpha = .13f),Offset(x+w*.08f,y+h*.05f),Size(w*.58f,h*.24f),CornerRadius(12.dp.toPx()))
        val lens = if(d.camera >= 3) 3 else 2
        repeat(lens) { n -> val cx = x+w*(if(n == 2) .47f else .25f); val cy = y+h*(if(n == 1) .21f else .11f); drawCircle(Color(0xFF182231),w*.105f,Offset(cx,cy)); drawCircle(Color(0xFF45617C),w*.065f,Offset(cx,cy)); drawCircle(Color.White.copy(alpha=.4f),w*.018f,Offset(cx-2,cy-2)) }
        val fx = x+w*.77f; val fy = y+h*.07f
        drawRoundRect(Color(0xFF070D17),Offset(fx,fy),Size(w,h*.94f),CornerRadius(21.dp.toPx()))
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF17394A),Color(0xFF599F92),Color(0xFFB8AFF8)),Offset(fx,fy),Offset(fx+w,fy+h)),Offset(fx+4,fy+4),Size(w-8,h*.94f-8),CornerRadius(18.dp.toPx()))
        drawCircle(Color(0xFF0C1722),w*.038f,Offset(fx+w*.5f,fy+13))
        drawRoundRect(Color.White.copy(alpha = .7f),Offset(fx+w*.35f,fy+h*.89f),Size(w*.3f,3.dp.toPx()),CornerRadius(2.dp.toPx()))
    }
}
