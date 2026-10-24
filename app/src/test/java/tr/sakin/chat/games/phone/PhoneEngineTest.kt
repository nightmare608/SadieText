package tr.sakin.chat.games.phone

import org.junit.Assert.*
import org.junit.Test

class PhoneEngineTest {
    private val company = PhoneEngine.found("Sadie Mobile")
    private val design = Blueprint(name = "One")
    private fun launched(count: Int = 40, price: Long = 5990) = PhoneEngine.launch(company,design,count,price)
    private fun denied(block: () -> Unit) { try { block(); fail("Invalid action accepted") } catch(_: GameRule) { } }
    @Test fun catalogHasUniqueRealFamilyNamesAndValidGameStats() {
        assertTrue(ChipCatalog.all.size >= 300)
        assertEquals(ChipCatalog.all.size,ChipCatalog.all.map { it.id }.distinct().size)
        ChipCatalog.all.forEach { assertTrue(it.power in 1..100); assertTrue(it.efficiency in 1..100); assertTrue(it.cost > 0); assertTrue(it.tier in 0..3) }
    }
    @Test fun launchDeductsDevelopmentAndInventoryButDoesNotPretendToSell() {
        val s = launched(); val p = s.products.single()
        assertEquals(START_CASH - PhoneEngine.development(design) - p.unitCost*40,s.cash)
        assertEquals(40,p.inventory); assertEquals(0,p.sold)
        assertEquals(-PhoneEngine.development(design),s.ledger.sumOf { it.profit })
        assertEquals(START_CASH + s.ledger.sumOf { it.cash },s.cash)
    }
    @Test fun accountingBalancesAfterSalesReturnsAdsInterestAndInvestment() {
        var s = PhoneEngine.borrow(launched())
        s = PhoneEngine.marketing(s,2)
        s = PhoneEngine.upgrade(s,"quality")
        s = PhoneEngine.nextWeek(s)
        assertEquals(START_CASH + s.ledger.sumOf { it.cash },s.cash)
        val assets = s.cash + s.products.sumOf { it.inventory*it.unitCost } - s.debt
        assertEquals(START_CASH+s.ledger.sumOf { it.profit },assets)
        assertEquals(s.ledger.filter { it.week == 1 }.sumOf { it.profit },s.reports.single().profit)
    }
    @Test fun salesNeverExceedStockAndReturnsNeverExceedSales() {
        var s = launched(10)
        repeat(20) { s = PhoneEngine.nextWeek(s); val p = s.products.single(); assertTrue(p.inventory >= 0); assertTrue(p.returned <= p.sold); assertEquals(p.made,p.inventory+p.sold) }
        assertEquals(10,s.products.single().sold)
    }
    @Test fun overpricedPhoneSellsLess() {
        val reasonable = PhoneEngine.nextWeek(launched(80,5990)).products.single()
        val expensive = PhoneEngine.nextWeek(launched(80,99000)).products.single()
        assertTrue(reasonable.sold > expensive.sold)
        assertTrue(reasonable.likes > expensive.likes)
    }
    @Test fun cheaperThanCostCanLoseMoney() {
        val s = PhoneEngine.nextWeek(launched(40,100))
        assertTrue(s.reports.single().profit < 0)
    }
    @Test fun stoppedModelDoesNotSell() {
        val s = PhoneEngine.nextWeek(PhoneEngine.toggle(launched(),1))
        assertEquals(0,s.products.single().sold)
    }
    @Test fun priceAndProductionHaveRealEffects() {
        var s = launched(10)
        s = PhoneEngine.price(s,1,7990)
        val cash = s.cash; val cost = s.products.single().unitCost
        s = PhoneEngine.produce(s,1,5)
        assertEquals(7990,s.products.single().price); assertEquals(15,s.products.single().inventory)
        assertEquals(cash - cost*5,s.cash)
    }
    @Test fun blocksUnaffordableOrInvalidLaunches() {
        denied { PhoneEngine.launch(company,design,1000,5990) }
        denied { PhoneEngine.launch(company,design,-10,5990) }
        denied { PhoneEngine.launch(company,design,20,0) }
        denied { PhoneEngine.launch(company,design.copy(ram=99),20,5990) }
        denied { PhoneEngine.launch(company,design.copy(screen=20),20,5990) }
        denied { PhoneEngine.launch(company,design.copy(chipId="missing"),20,5990) }
    }
    @Test fun blocksDuplicateModelAndLockedHardware() {
        denied { PhoneEngine.launch(launched(),design,10,5990) }
        val chip = ChipCatalog.all.first { it.tier == 3 }
        denied { PhoneEngine.launch(company,design.copy(chipId=chip.id),10,5990) }
        denied { PhoneEngine.launch(company,design.copy(ram=24),10,5990) }
    }
    @Test fun researchUnlocksFlagshipsAndFactoryLowersFutureCost() {
        val rich = company.copy(cash = 5_000_000)
        val researched = PhoneEngine.upgrade(PhoneEngine.upgrade(rich,"research"),"research")
        assertEquals(3,researched.research)
        PhoneEngine.validate(design.copy(chipId=ChipCatalog.all.first { it.tier == 3 }.id,ram=24),researched)
        assertTrue(PhoneEngine.cost(design,PhoneEngine.upgrade(rich,"factory")) < PhoneEngine.cost(design,rich))
        denied { PhoneEngine.upgrade(researched,"research") }
    }
    @Test fun creditIsNotProfitAndRepaymentHasNoProfitEffect() {
        var s = PhoneEngine.borrow(company)
        assertEquals(600_000,s.cash); assertEquals(100_000,s.debt); assertEquals(0,s.ledger.sumOf { it.profit })
        s = PhoneEngine.repay(s)
        assertEquals(START_CASH,s.cash); assertEquals(0,s.debt)
        assertEquals(0,s.ledger.sumOf { it.profit })
        repeat(4) { s = PhoneEngine.borrow(s) }
        denied { PhoneEngine.borrow(s) }
    }
    @Test fun marketingIsOnlyChargedWhenWeekAdvances() {
        val s = PhoneEngine.marketing(company,3)
        assertEquals(company.cash,s.cash)
        val n = PhoneEngine.nextWeek(s)
        assertEquals(1,n.ledger.count { it.title == "Pazarlama kampanyası" })
        assertEquals(-19500,n.ledger.first { it.title == "Pazarlama kampanyası" }.cash)
    }
    @Test fun deterministicWeekCannotBeRerolledByOpeningGame() {
        assertEquals(PhoneEngine.nextWeek(launched()),PhoneEngine.nextWeek(launched()))
    }
    @Test fun saveRoundTripPreservesAllAccountingAndGameplay() {
        val s = PhoneEngine.nextWeek(PhoneEngine.borrow(launched()))
        assertEquals(s,PhoneSave.decode(PhoneSave.encode(s)))
    }
    @Test fun rejectsUnsupportedAndTruncatedSave() {
        assertThrows(IllegalArgumentException::class.java) { PhoneSave.decode(PhoneSave.encode(company).replace("\"version\":1","\"version\":9")) }
        assertThrows(Exception::class.java) { PhoneSave.decode("{") }
    }
    @Test fun longCampaignHasBoundedHistoryAndNoStockUnderflow() {
        var s = launched()
        repeat(150) { s = PhoneEngine.nextWeek(s) }
        assertEquals(104,s.reports.size); assertTrue(s.ledger.size <= 350)
        assertEquals(0,s.products.single().inventory)
    }
}
