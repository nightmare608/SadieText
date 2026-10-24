package tr.sakin.chat.games.phone

/** Names identify real product families; all prices and scores are fictional game balancing. */
data class Chip(val id: String, val name: String, val maker: String, val power: Int, val efficiency: Int, val cost: Int, val tier: Int)
object ChipCatalog {
    private fun family(maker: String, prefix: String, names: String, base: Int, step: Double = 1.1): List<Chip> =
        names.split('|').mapIndexed { i, name ->
            val power = (base + i * step).toInt().coerceIn(8, 100)
            val full = "$prefix$name"
            Chip(full.lowercase().replace("+", " plus ").replace(Regex("[^a-z0-9]+"), "-"), full, maker, power,
                (35 + power * .56).toInt().coerceAtMost(96), 8 + power * power / 42, when { power < 44 -> 0; power < 68 -> 1; power < 86 -> 2; else -> 3 })
        }
    val all: List<Chip> = buildList {
        addAll(family("Qualcomm", "Snapdragon ", "200|208|210|212|215|400|410|412|415|425|427|429|430|435|439|450|460|480|480+|4 Gen 1|4s Gen 2|4 Gen 2", 10, 1.5))
        addAll(family("Qualcomm", "Snapdragon ", "600|610|615|616|617|625|626|630|636|650|652|653|660|662|665|670|675|680|685|690|695|6s 4G Gen 1|6s Gen 3|6 Gen 1|6 Gen 3|6 Gen 4|6s Gen 4", 23, 1.25))
        addAll(family("Qualcomm", "Snapdragon ", "710|712|720G|730|730G|732G|750G|765|765G|768G|778G|778G+|780G|782G|7s Gen 2|7 Gen 1|7s Gen 3|7 Gen 3|7+ Gen 2|7+ Gen 3|7 Gen 4", 39, 1.7))
        addAll(family("Qualcomm", "Snapdragon ", "800|801|805|808|810|820|821|835|845|855|855+|860|865|865+|870|888|888+|8 Gen 1|8+ Gen 1|8s Gen 3|8 Gen 2|8 Gen 3|8s Gen 4|8 Elite|8 Gen 5|8 Elite Gen 5", 33, 2.65))
        addAll(family("MediaTek", "Helio ", "A20|A22|A25|P10|P20|P22|P23|P25|P30|P35|P60|P65|P70|P90|P95|X10|X20|X25|X27|X30", 12, 1.5))
        addAll(family("MediaTek", "Helio ", "G25|G35|G36|G37|G50|G70|G80|G81|G85|G88|G90|G90T|G91|G92|G95|G96|G99|G99+|G100|G200", 23, 1.45))
        addAll(family("MediaTek", "Dimensity ", "6020|6100+|6300|6400|700|720|800|800U|810|820|900|920|930|1000|1000+|1080|1100|1200|1300|7020|7025|7030|7050|7060|7200|7300|7350|7400|8000|8100|8200|8250|8300|8350|8400|8500|9000|9000+|9200|9200+|9300|9300+|9400|9400+|9500", 32, 1.48))
        addAll(family("Samsung", "Exynos ", "3475|4412|5250|5410|5420|5430|5433|7420|7570|7580|7870|7880|7884|7885|7904|850|880|9609|9610|9611|980|9810|9820|9825|990|1080|1280|1330|1380|1480|1580|1680|2100|2200|2400|2400e|2500|2600", 13, 2.2))
        addAll(family("Apple", "Apple ", "A4|A5|A6|A7|A8|A9|A10 Fusion|A11 Bionic|A12 Bionic|A13 Bionic|A14 Bionic|A15 Bionic|A16 Bionic|A17 Pro|A18|A18 Pro|A19|A19 Pro|A20 Pro", 12, 4.9))
        addAll(family("Google", "Tensor", "| G2| G3| G4| G5", 57, 6.5))
        addAll(family("HiSilicon", "Kirin ", "620|650|655|658|659|710|710A|810|820|920|925|930|950|955|960|970|980|985|990|990 5G|9000|9000E|9000S|9010|9020", 18, 2.65))
        addAll(family("UNISOC", "UNISOC ", "SC7731E|SC9832E|SC9863A|T310|T606|T610|T612|T616|T618|T700|T750|T760|T765|T770|T820|T8300", 10, 3.0))
        addAll(family("NVIDIA", "Tegra ", "2|3|4|4i|K1|X1", 16, 5.0))
        addAll(family("Intel", "Atom ", "Z2420|Z2460|Z2480|Z2560|Z2580|Z3460|Z3480|Z3560|Z3580|x3-C3130|x3-C3230RK|x3-C3440", 13, 2.0))
        addAll(family("Texas Instruments", "OMAP ", "3430|3630|4430|4460|4470|5430|5432", 10, 3.0))
    }.also { chips -> require(chips.map { it.id }.distinct().size == chips.size) }
    fun get(id: String): Chip = all.firstOrNull { it.id == id } ?: all.first { it.name == "Helio G99" }
    val defaultId = all.first { it.name == "Helio G99" }.id
}
