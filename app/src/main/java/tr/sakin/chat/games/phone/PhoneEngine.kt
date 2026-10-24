package tr.sakin.chat.games.phone

import kotlin.math.*
import kotlin.random.Random

enum class Audience(val label: String) { VALUE("Fiyat / performans"), GAMING("Oyuncular"), CAMERA("Fotoğrafçılar"), PREMIUM("Premium") }
data class Part(val label: String, val cost: Int, val score: Int)
object Parts {
    val screens = listOf(Part("LCD · 60 Hz", 15, 25), Part("IPS · 90 Hz", 24, 42), Part("OLED · 90 Hz", 42, 62), Part("AMOLED · 120 Hz", 64, 84), Part("LTPO OLED · 144 Hz", 92, 100))
    val cameras = listOf(Part("13 MP · giriş sensörü", 8, 20), Part("50 MP · standart", 22, 47), Part("50 MP · OIS", 45, 73), Part("108 MP · OIS + geniş açı", 69, 86), Part("50 MP · büyük sensör + telefoto", 99, 100))
    val bodies = listOf(Part("Polikarbonat", 9, 25), Part("Cam ve alüminyum", 27, 70), Part("Titanyum çerçeve", 52, 100))
    val cooling = listOf(Part("Grafit", 3, 30), Part("Bakır ısı borusu", 12, 65), Part("Buhar odası", 24, 100))
    val ram = listOf(3, 4, 6, 8, 12, 16, 24)
    val storage = listOf(32, 64, 128, 256, 512, 1024)
    val batteries = listOf(3000, 4000, 5000, 6000, 7000)
    val colors = listOf("Gece mavisi", "Lavanta", "Buz beyazı", "Mercan", "Adaçayı")
}
data class Blueprint(val name: String = "One", val chipId: String = ChipCatalog.defaultId, val ram: Int = 6, val storage: Int = 128,
    val screen: Int = 2, val camera: Int = 2, val battery: Int = 5000, val body: Int = 0, val cooling: Int = 0,
    val support: Int = 2, val color: Int = 0, val audience: Audience = Audience.VALUE)
data class Phone(val id: Int, val design: Blueprint, val unitCost: Long, val price: Long, val inventory: Int, val made: Int,
    val sold: Int = 0, val returned: Int = 0, val born: Int, val active: Boolean = true, val likes: Int = 0, val review: String = "İlk satışlar bekleniyor.")
data class Entry(val week: Int, val title: String, val cash: Long, val profit: Long)
data class WeekReport(val week: Int, val revenue: Long, val profit: Long, val sold: Int, val cash: Long)
data class Company(val name: String = "", val cash: Long = START_CASH, val week: Int = 1, val reputation: Int = 30,
    val research: Int = 1, val factory: Int = 1, val quality: Int = 1, val staff: Int = 1, val marketing: Int = 0,
    val debt: Long = 0, val products: List<Phone> = emptyList(), val ledger: List<Entry> = emptyList(), val reports: List<WeekReport> = emptyList(),
    val nextId: Int = 1, val lastNews: String = "İlk modelini tasarla; üretim ve geliştirme maliyetine bütçe ayır.", val seed: Int = 42)
const val START_CASH = 500_000L
class GameRule(message: String) : IllegalArgumentException(message)
object PhoneEngine {
    fun money(v: Long) = "₺" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("tr-TR")).format(v)
    fun capacity(s: Company) = 500 + s.factory * 500
    fun cost(d: Blueprint, s: Company): Long = ((ChipCatalog.get(d.chipId).cost + d.ram * 3 + d.storage / 9 + Parts.screens[d.screen].cost +
        Parts.cameras[d.camera].cost + d.battery / 200 + Parts.bodies[d.body].cost + Parts.cooling[d.cooling].cost + d.support * 5 + 18) * 13L * (1.0 - (s.factory - 1) * .025)).toLong()
    fun development(d: Blueprint) = 15_000L + ChipCatalog.get(d.chipId).tier * 12_000L + d.screen * 3500 + d.camera * 3500
    fun qualities(d: Blueprint, s: Company): List<Int> {
        val chip = ChipCatalog.get(d.chipId)
        val thermal = max(0, chip.power - Parts.cooling[d.cooling].score - 25) / 3
        return listOf((chip.power * .7 + d.ram * 1.5 + d.storage / 64.0 - thermal).toInt().coerceIn(5, 100),
            (Parts.cameras[d.camera].score * .8 + s.research * 3).toInt().coerceAtMost(100),
            (d.battery / 100.0 + chip.efficiency * .35 - d.screen * 3).toInt().coerceIn(5, 100),
            (Parts.screens[d.screen].score * .5 + Parts.bodies[d.body].score * .3 + d.support * 4).toInt().coerceAtMost(100))
    }
    fun quality(d: Blueprint, s: Company): Int {
        val q = qualities(d, s)
        val weights = when (d.audience) { Audience.VALUE -> listOf(.30,.15,.40,.15); Audience.GAMING -> listOf(.65,.05,.20,.10); Audience.CAMERA -> listOf(.15,.60,.10,.15); Audience.PREMIUM -> listOf(.20,.25,.10,.45) }
        return q.zip(weights).sumOf { it.first * it.second }.toInt()
    }
    private fun check(ok: Boolean, message: String) { if (!ok) throw GameRule(message) }
    private fun booked(s: Company, title: String, cash: Long, profit: Long = cash): Company =
        s.copy(cash = s.cash + cash, ledger = (s.ledger + Entry(s.week, title, cash, profit)).takeLast(350))
    fun found(name: String): Company { check(name.trim().length in 2..30, "Şirket adı 2–30 karakter olmalı."); return Company(name = name.trim()) }
    fun validate(d: Blueprint, s: Company) {
        check(d.name.trim().length in 1..30, "Model adı 1–30 karakter olmalı.")
        check(ChipCatalog.all.any { it.id == d.chipId }, "İşlemci bulunamadı.")
        check(d.ram in Parts.ram && d.storage in Parts.storage && d.battery in Parts.batteries && d.screen in Parts.screens.indices && d.camera in Parts.cameras.indices && d.body in Parts.bodies.indices && d.cooling in Parts.cooling.indices && d.color in Parts.colors.indices && d.support in 1..5, "Parça seçimi geçersiz.")
        check(ChipCatalog.get(d.chipId).tier <= s.research, "Bu işlemci için Ar-Ge seviyesini yükselt.")
        check(d.screen <= s.research + 1 && d.camera <= s.research + 1, "Bu parça için Ar-Ge seviyesini yükselt.")
        check(d.ram <= listOf(6, 8, 16, 24)[s.research] && d.storage <= listOf(128, 256, 512, 1024)[s.research], "RAM/depolama için Ar-Ge seviyesini yükselt.")
    }
    fun launch(s: Company, d: Blueprint, count: Int, price: Long): Company {
        validate(d, s)
        check(s.products.size < 100, "En fazla 100 model oluşturabilirsin.")
        check(s.products.none { it.design.name.equals(d.name.trim(), true) }, "Bu model adı kullanılıyor.")
        check(count in 10..capacity(s), "İlk üretim 10–${capacity(s)} adet olmalı.")
        check(price in 100..100_000, "Fiyat ₺100–₺100.000 arasında olmalı.")
        val unit = cost(d, s); val dev = development(d); val total = unit * count + dev
        check(s.cash >= total, "Yetersiz nakit. Daha küçük üretim veya daha ucuz parça seç.")
        var n = booked(s, "${d.name} geliştirme", -dev)
        n = booked(n, "${d.name} · $count adet üretim", -unit * count, 0)
        return n.copy(products = n.products + Phone(s.nextId, d.copy(name = d.name.trim()), unit, price, count, count, born = s.week), nextId = s.nextId + 1, lastNews = "${d.name} satışta! Hafta ilerledikçe talep ve yorumlar oluşacak.")
    }
    fun produce(s: Company, id: Int, count: Int): Company {
        val p = s.products.first { it.id == id }
        check(p.active, "Önce modeli satışa aç.")
        check(count in 1..capacity(s) && p.inventory + count <= 20_000, "Üretim kapasitesini veya stok sınırını aşıyorsun.")
        check(s.cash >= p.unitCost * count, "Üretim için nakit yetersiz.")
        return booked(s, "${p.design.name} · $count adet üretim", -p.unitCost * count, 0).copy(products = s.products.map { if (it.id == id) it.copy(inventory = it.inventory + count, made = it.made + count) else it })
    }
    fun price(s: Company, id: Int, price: Long): Company { check(price in 100..100_000, "Fiyat ₺100–₺100.000 olmalı."); return s.copy(products = s.products.map { if (it.id == id) it.copy(price = price) else it }) }
    fun toggle(s: Company, id: Int) = s.copy(products = s.products.map { if (it.id == id) it.copy(active = !it.active) else it })
    fun upgradeCost(s: Company, type: String): Long = when(type) { "research" -> 100_000L * s.research; "factory" -> 80_000L * s.factory; "quality" -> 65_000L * s.quality; else -> 35_000L * s.staff }
    fun upgrade(s: Company, type: String): Company {
        check(type in listOf("research","factory","quality","staff"), "Yükseltme bulunamadı.")
        val level = when(type) { "research" -> s.research; "factory" -> s.factory; "quality" -> s.quality; else -> s.staff }
        check(level < if (type == "research") 3 else 5, "En yüksek seviyedesin.")
        val amount = upgradeCost(s, type); check(s.cash >= amount, "Yükseltme için nakit yetersiz.")
        val title = when(type) { "research" -> "Ar-Ge"; "factory" -> "Fabrika"; "quality" -> "Kalite kontrol"; else -> "Ekip" }
        return booked(s, "$title yatırımı", -amount).copy(research = s.research + if(type == "research") 1 else 0, factory = s.factory + if(type == "factory") 1 else 0,
            quality = s.quality + if(type == "quality") 1 else 0, staff = s.staff + if(type == "staff") 1 else 0)
    }
    fun marketing(s: Company, level: Int): Company { check(level in 0..3, "Geçersiz kampanya."); return s.copy(marketing = level) }
    fun borrow(s: Company): Company { check(s.debt < 400_000, "Kredi limiti ₺400.000."); return booked(s, "Kredi alındı", 100_000, 0).copy(debt = s.debt + 100_000) }
    fun repay(s: Company): Company { val amount = min(100_000L, s.debt); check(amount > 0 && s.cash >= amount, "Ödeme için yeterli nakit veya borç yok."); return booked(s, "Kredi anapara ödemesi", -amount, 0).copy(debt = s.debt - amount) }
    val news = listOf("Pazar dengeli. Müşteriler fiyat ve kaliteyi karşılaştırıyor.", "Oyun haftası! Oyuncu telefonlarına talep yükseldi.", "Fotoğraf akımı başladı. Kamera odaklı modeller ilgi görüyor.", "Tasarruf haftası: uygun fiyatlı modeller öne çıkıyor.", "Premium lansman sezonu: tasarım ve uzun güncelleme desteği aranıyor.", "Parça tedarikinde belirsizlik: müşteriler alışverişi yavaşlatıyor.")
    fun nextWeek(s: Company): Company {
        check(s.name.isNotBlank(), "Önce şirketini kur.")
        val rng = Random(s.seed + s.week * 7919); val event = rng.nextInt(news.size)
        var n = s; var revenue = 0L; var profit = 0L; var totalSold = 0; var likesWeighted = 0
        val products = s.products.map { p ->
            if (!p.active || p.inventory == 0) return@map p
            val q = quality(p.design, s)
            val fairPrice = 1800 + q * 115
            val value = (fairPrice.toDouble() / p.price).coerceIn(.12, 2.4)
            val satisfaction = (q * .63 + (value * 25).coerceAtMost(38.0) + s.quality * 2 - max(0, s.week - p.born - p.design.support * 8) * .4).toInt().coerceIn(4, 98)
            val trending = when(event) { 1 -> Audience.GAMING; 2 -> Audience.CAMERA; 3 -> Audience.VALUE; 4 -> Audience.PREMIUM; else -> null }
            val trend = if (p.design.audience == trending) 1.55 else if(event == 5) .65 else 1.0
            val competing = max(1, s.products.count { it.active && it.inventory > 0 && it.design.audience == p.design.audience })
            val age = (1.0 - (s.week - p.born) * .012).coerceAtLeast(.2)
            val demand = ((25 + s.reputation * 1.6 + s.marketing * 35 + s.staff * 8) * value.pow(1.7) * trend * age * rng.nextDouble(.8,1.2) / sqrt(competing.toDouble())).toInt().coerceAtLeast(0)
            val sold = min(p.inventory, demand)
            val defectRate = (.055 - s.quality * .009 + if (ChipCatalog.get(p.design.chipId).power > 80 && p.design.cooling == 0) .035 else 0.0).coerceAtLeast(.005)
            val returns = (sold * defectRate).roundToInt().coerceAtMost(sold)
            val saleRevenue = (sold - returns) * p.price
            val saleProfit = saleRevenue - sold * p.unitCost - returns * 150L
            n = booked(n, "${p.design.name} · $sold satış, $returns iade", saleRevenue - returns * 150L, saleProfit)
            revenue += saleRevenue; profit += saleProfit; totalSold += sold; likesWeighted += satisfaction * sold
            val review = when { returns > sold * .05 && sold > 0 -> "Isınma ve arıza şikâyetleri var. Soğutma / kalite kontrolü iyileştir."; value < .7 -> "Özelliklerine göre fiyatı yüksek bulduk."; satisfaction >= 80 -> "Dengeli, beklentilerimizin üstünde. Tavsiye ediyoruz!"; q < 45 -> "Günlük işler için yeterli, güçlü uygulamalarda zorlanıyor."; else -> "İyi bir seçenek; fiyat ve özellik dengesi geliştirilebilir." }
            p.copy(inventory = p.inventory - sold, sold = p.sold + sold, returned = p.returned + returns, likes = if(sold > 0) satisfaction else p.likes, review = if(sold > 0) review else p.review)
        }
        val overhead = 2500L + s.staff * 1600 + s.factory * 900
        val ads = s.marketing * 6500L; val interest = s.debt / 100
        n = booked(n, "Haftalık işletme gideri", -overhead)
        if (ads > 0) n = booked(n, "Pazarlama kampanyası", -ads)
        if (interest > 0) n = booked(n, "Haftalık kredi faizi (%1)", -interest)
        profit = n.ledger.filter { it.week == s.week }.sumOf { it.profit }
        val rep = if(totalSold == 0) s.reputation else (s.reputation * .85 + (likesWeighted.toDouble() / totalSold) * .15).roundToInt().coerceIn(1, 100)
        return n.copy(week = s.week + 1, products = products, reputation = rep,
            reports = (s.reports + WeekReport(s.week, revenue, profit, totalSold, n.cash)).takeLast(104), lastNews = news[event] + " $totalSold adet satıldı; haftalık sonuç ${money(profit)}.")
    }
}
