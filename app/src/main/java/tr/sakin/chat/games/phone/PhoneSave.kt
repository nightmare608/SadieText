package tr.sakin.chat.games.phone

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object PhoneSave {
    private fun Blueprint.json() = JSONObject().put("name",name).put("chip",chipId).put("ram",ram).put("storage",storage)
        .put("screen",screen).put("camera",camera).put("battery",battery).put("body",body).put("cooling",cooling)
        .put("support",support).put("color",color).put("audience",audience.name)
    private fun design(o: JSONObject) = Blueprint(o.getString("name"),o.getString("chip"),o.getInt("ram"),o.getInt("storage"),o.getInt("screen"),o.getInt("camera"),o.getInt("battery"),o.getInt("body"),o.getInt("cooling"),o.getInt("support"),o.getInt("color"),Audience.valueOf(o.getString("audience")))
    private fun <T> array(values: List<T>, json: (T) -> JSONObject) = JSONArray().also { a -> values.forEach { a.put(json(it)) } }
    private fun <T> list(a: JSONArray, parse: (JSONObject) -> T): List<T> = (0 until a.length()).map { parse(a.getJSONObject(it)) }
    fun encode(s: Company): String = JSONObject().put("version",1).put("name",s.name).put("cash",s.cash).put("week",s.week)
        .put("reputation",s.reputation).put("research",s.research).put("factory",s.factory).put("quality",s.quality).put("staff",s.staff)
        .put("marketing",s.marketing).put("debt",s.debt).put("nextId",s.nextId).put("news",s.lastNews).put("seed",s.seed)
        .put("products",array(s.products) { p -> JSONObject().put("id",p.id).put("design",p.design.json()).put("unitCost",p.unitCost).put("price",p.price)
            .put("inventory",p.inventory).put("made",p.made).put("sold",p.sold).put("returned",p.returned).put("born",p.born).put("active",p.active).put("likes",p.likes).put("review",p.review) })
        .put("ledger",array(s.ledger) { e -> JSONObject().put("week",e.week).put("title",e.title).put("cash",e.cash).put("profit",e.profit) })
        .put("reports",array(s.reports) { r -> JSONObject().put("week",r.week).put("revenue",r.revenue).put("profit",r.profit).put("sold",r.sold).put("cash",r.cash) }).toString()
    fun decode(text: String): Company {
        val o = JSONObject(text); require(o.getInt("version") == 1) { "Desteklenmeyen kayıt sürümü" }
        val s = Company(o.getString("name"),o.getLong("cash"),o.getInt("week"),o.getInt("reputation"),o.getInt("research"),o.getInt("factory"),o.getInt("quality"),o.getInt("staff"),o.getInt("marketing"),o.getLong("debt"),
            list(o.getJSONArray("products")) { p -> Phone(p.getInt("id"),design(p.getJSONObject("design")),p.getLong("unitCost"),p.getLong("price"),p.getInt("inventory"),p.getInt("made"),p.getInt("sold"),p.getInt("returned"),p.getInt("born"),p.getBoolean("active"),p.getInt("likes"),p.getString("review")) },
            list(o.getJSONArray("ledger")) { e -> Entry(e.getInt("week"),e.getString("title"),e.getLong("cash"),e.getLong("profit")) },
            list(o.getJSONArray("reports")) { r -> WeekReport(r.getInt("week"),r.getLong("revenue"),r.getLong("profit"),r.getInt("sold"),r.getLong("cash")) },o.getInt("nextId"),o.getString("news"),o.getInt("seed"))
        require(s.research in 0..3 && s.factory in 1..5 && s.quality in 1..5 && s.staff in 1..5 && s.marketing in 0..3 && s.week > 0 && s.debt in 0..400_000 && s.products.size <= 100)
        s.products.forEach { p -> PhoneEngine.validate(p.design, s); require(p.inventory >= 0 && p.sold >= p.returned && p.made == p.inventory + p.sold) }
        return s
    }
}
data class PhoneUiState(val company: Company? = null, val loading: Boolean = true, val saving: Boolean = false, val error: String? = null)
class PhoneViewModel(app: Application, scope: String) : ViewModel() {
    private val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) }
    private val file = AtomicFile(File(app.filesDir, "phone-company-$digest.json"))
    private val mutable = MutableStateFlow(PhoneUiState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch {
        val loaded = withContext(Dispatchers.IO) { runCatching { if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) PhoneSave.decode(file.openRead().bufferedReader().use { it.readText() }) else Company() } }
        mutable.value = loaded.fold({ PhoneUiState(it, loading = false) }, { PhoneUiState(loading = false, error = "Oyun kaydı açılamadı. Kaydın korunuyor; tekrar açmayı deneyebilirsin.") })
    } }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    fun change(action: (Company) -> Company) {
        val old = mutable.value
        if (old.loading || old.saving || old.company == null) return
        val updated = try { action(old.company) } catch(e: GameRule) { mutable.value = old.copy(error = e.message); return }
        persist(updated, old)
    }
    fun newCompany(name: String) {
        if(mutable.value.loading || mutable.value.saving) return
        val company = try { PhoneEngine.found(name) } catch(e: GameRule) { mutable.value = mutable.value.copy(error = e.message); return }
        persist(company, mutable.value)
    }
    private fun persist(company: Company, old: PhoneUiState) {
        mutable.value = old.copy(saving = true, error = null)
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching {
                val output = file.startWrite()
                try { output.write(PhoneSave.encode(company).toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
                catch(e: Exception) { file.failWrite(output); throw e }
            } }
            mutable.value = if(saved.isSuccess) PhoneUiState(company, loading = false) else old.copy(saving = false, error = "Kayıt yazılamadı; işlem uygulanmadı. Depolama alanını kontrol et.")
        }
    }
    class Factory(private val app: Application, private val scope: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = PhoneViewModel(app, scope) as T
    }
}
