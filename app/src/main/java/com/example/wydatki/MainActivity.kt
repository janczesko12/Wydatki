package com.example.wydatki

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max

data class Category(val id: String, val name: String, val emoji: String, val sort: Int = 0)
data class Expense(val id: String, val categoryId: String, val amount: Double, val description: String, val date: String, val note: String = "")
data class Income(val id: String, val source: String, val amount: Double, val date: String, val note: String = "")
data class ReleaseInfo(val tag: String, val apkUrl: String)

class MainActivity : ComponentActivity() {
    private lateinit var store: AppStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AppStore(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFFF6F8F7)) {
                    WydatkiApp(store)
                }
            }
        }
    }
}

class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("wydatki_data", Context.MODE_PRIVATE)
    var categories by mutableStateOf(loadCategories())
        private set
    var expenses by mutableStateOf(loadExpenses())
        private set
    var incomes by mutableStateOf(loadIncomes())
        private set
    var cloudStatus by mutableStateOf("Chmura: przygotowywanie…")
        private set

    init {
        FirebaseCloud.start(context, this)
    }

    fun addExpense(x: Expense) {
        expenses = expenses + x; saveAll(); FirebaseCloud.push(this)
    }

    fun updateExpense(x: Expense) {
        expenses =
            expenses.map { if (it.id == x.id) x else it }; saveAll(); FirebaseCloud.push(this)
    }

    fun deleteExpense(id: String) {
        expenses = expenses.filterNot { it.id == id }; saveAll(); FirebaseCloud.push(this)
    }

    fun addIncome(x: Income) {
        incomes = incomes + x; saveAll(); FirebaseCloud.push(this)
    }

    fun updateIncome(x: Income) {
        incomes = incomes.map { if (it.id == x.id) x else it }; saveAll(); FirebaseCloud.push(this)
    }

    fun deleteIncome(id: String) {
        incomes = incomes.filterNot { it.id == id }; saveAll(); FirebaseCloud.push(this)
    }

    fun addCategory(x: Category) {
        categories = categories + x; saveAll(); FirebaseCloud.push(this)
    }

    fun updateCategory(x: Category) {
        categories = categories.map { if (it.id == x.id) x else it }; saveAll(); FirebaseCloud.push(
            this
        )
    }

    fun deleteCategory(id: String) {
        if (expenses.any { it.categoryId == id }) return
        categories = categories.filterNot { it.id == id }; saveAll(); FirebaseCloud.push(this)
    }

    fun replaceAll(c: List<Category>, e: List<Expense>, i: List<Income>, push: Boolean = false) {
        categories = c
        expenses = e
        incomes = i
        saveAll()
        if (push) FirebaseCloud.push(this)
    }

    fun updateCloudStatus(value: String) {
        cloudStatus = value
    }

    fun exportJson(): String {
        return JSONObject().apply {
            put("version", 1)
            put("categories", JSONArray().apply {
                categories.forEach {
                    put(JSONObject().apply {
                        put("id", it.id); put("name", it.name); put("emoji", it.emoji); put(
                        "sort",
                        it.sort
                    )
                    })
                }
            })
            put("expenses", JSONArray().apply {
                expenses.forEach {
                    put(JSONObject().apply {
                        put("id", it.id); put("categoryId", it.categoryId); put("amount", it.amount)
                        put("description", it.description); put("date", it.date); put(
                        "note",
                        it.note
                    )
                    })
                }
            })
            put("incomes", JSONArray().apply {
                incomes.forEach {
                    put(JSONObject().apply {
                        put("id", it.id); put("source", it.source); put(
                        "amount",
                        it.amount
                    ); put("date", it.date); put("note", it.note)
                    })
                }
            })
        }.toString(2)
    }

    fun importJson(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            val ca = o.getJSONArray("categories")
            val ea = o.getJSONArray("expenses")
            val ia = o.getJSONArray("incomes")
            val c = List(ca.length()) { n ->
                ca.getJSONObject(n).let {
                    Category(
                        it.getString("id"),
                        it.getString("name"),
                        it.getString("emoji"),
                        it.optInt("sort")
                    )
                }
            }
            val e = List(ea.length()) { n ->
                ea.getJSONObject(n).let {
                    Expense(
                        it.getString("id"),
                        it.getString("categoryId"),
                        it.getDouble("amount"),
                        it.getString("description"),
                        it.getString("date"),
                        it.optString("note")
                    )
                }
            }
            val i = List(ia.length()) { n ->
                ia.getJSONObject(n).let {
                    Income(
                        it.getString("id"),
                        it.getString("source"),
                        it.getDouble("amount"),
                        it.getString("date"),
                        it.optString("note")
                    )
                }
            }
            replaceAll(c, e, i, true)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveAll() {
        prefs.edit {
            putString("categories", categoriesJson())
            putString("expenses", expensesJson())
            putString("incomes", incomesJson())
        }
    }

    private fun categoriesJson() = JSONArray().apply {
        categories.forEach {
            put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("emoji", it.emoji); put("sort", it.sort)
            })
        }
    }.toString()

    private fun expensesJson() = JSONArray().apply {
        expenses.forEach {
            put(JSONObject().apply {
                put("id", it.id); put("categoryId", it.categoryId); put(
                "amount",
                it.amount
            ); put("description", it.description); put("date", it.date); put("note", it.note)
            })
        }
    }.toString()

    private fun incomesJson() = JSONArray().apply {
        incomes.forEach {
            put(JSONObject().apply {
                put("id", it.id); put("source", it.source); put("amount", it.amount); put(
                "date",
                it.date
            ); put("note", it.note)
            })
        }
    }.toString()

    private fun loadCategories(): List<Category> {
        val raw = prefs.getString("categories", null) ?: return defaultCategories()
        return try {
            val a = JSONArray(raw); List(a.length()) { n ->
                a.getJSONObject(n).let {
                    Category(
                        it.getString("id"),
                        it.getString("name"),
                        it.getString("emoji"),
                        it.optInt("sort")
                    )
                }
            }
        } catch (_: Exception) {
            defaultCategories()
        }
    }

    private fun loadExpenses(): List<Expense> {
        val raw = prefs.getString("expenses", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw); List(a.length()) { n ->
                a.getJSONObject(n).let {
                    Expense(
                        it.getString("id"),
                        it.getString("categoryId"),
                        it.getDouble("amount"),
                        it.getString("description"),
                        it.getString("date"),
                        it.optString("note")
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadIncomes(): List<Income> {
        val raw = prefs.getString("incomes", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw); List(a.length()) { n ->
                a.getJSONObject(n).let {
                    Income(
                        it.getString("id"),
                        it.getString("source"),
                        it.getDouble("amount"),
                        it.getString("date"),
                        it.optString("note")
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun defaultCategories() = listOf(
        Category("oplaty", "Opłaty", "🏠", 0), Category("gaz", "Gaz za cały dom", "🔥", 1),
        Category("jedzenie", "Jedzenie", "🛒", 2), Category("auto", "Auto", "🚗", 3),
        Category("uroda", "Uroda", "💄", 4), Category("rozrywka", "Rozrywka", "🎮", 5),
        Category("szkola", "Szkoła", "🎓", 6), Category("dom", "Art. do domu", "🏡", 7),
        Category("franek", "Ciuchy Franek", "👕", 8), Category("janek", "Ciuchy Janek", "👕", 9),
        Category("karolina", "Ciuchy Karolina", "👗", 10), Category("praca", "Praca", "💼", 11),
        Category("prezenty", "Prezenty", "🎁", 12), Category("zdrowie", "Zdrowie", "❤️", 13),
        Category("kredyt", "Rata kredytu", "🏦", 14), Category("inne", "Inne", "📦", 15)
    )



    fun importJsonFromCloud(raw: String) {
        try {
            val o = JSONObject(raw)
            val ca = o.getJSONArray("categories")
            val ea = o.getJSONArray("expenses")
            val ia = o.getJSONArray("incomes")
            val c = List(ca.length()) { n ->
                ca.getJSONObject(n).let {
                    Category(
                        it.getString("id"),
                        it.getString("name"),
                        it.getString("emoji"),
                        it.optInt("sort")
                    )
                }
            }
            val e = List(ea.length()) { n ->
                ea.getJSONObject(n).let {
                    Expense(
                        it.getString("id"),
                        it.getString("categoryId"),
                        it.getDouble("amount"),
                        it.getString("description"),
                        it.getString("date"),
                        it.optString("note")
                    )
                }
            }
            val i = List(ia.length()) { n ->
                ia.getJSONObject(n).let {
                    Income(
                        it.getString("id"),
                        it.getString("source"),
                        it.getDouble("amount"),
                        it.getString("date"),
                        it.optString("note")
                    )
                }
            }
            replaceAll(c, e, i, false)
            updateCloudStatus("Chmura: zsynchronizowano")
        } catch (_: Exception) {
            updateCloudStatus("Chmura: nieprawidłowe dane")
        }
    }
}

@SuppressLint("StaticFieldLeak")
object FirebaseCloud {
    private var db: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private var store: WeakReference<AppStore>? = null
    private var ready = false

    fun start(context: Context, appStore: AppStore) {
        store = WeakReference(appStore)

        try {
            val appContext = context.applicationContext
            val app = FirebaseApp.getApps(appContext).firstOrNull()
                ?: FirebaseApp.initializeApp(appContext)

            if (app == null) {
                appStore.updateCloudStatus("Chmura: błąd Firebase")
                return
            }

            auth = FirebaseAuth.getInstance(app)
            db = FirebaseFirestore.getInstance(app)

            if (auth?.currentUser != null) {
                signInReady()
            } else {
                appStore.updateCloudStatus("Chmura: zaloguj się")
            }
        } catch (e: Exception) {
            appStore.updateCloudStatus("Chmura: błąd konfiguracji")
        }
    }

    private fun signInReady() {
        ready = true
        store?.get()?.updateCloudStatus("Chmura: połączona")
        pull()
    }

    fun login(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            onResult(false, "Podaj e-mail i hasło.")
            return
        }

        val a = auth ?: run { onResult(false, "Firebase nie jest skonfigurowany."); return }
        a.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                ready = true
                store?.get()?.updateCloudStatus("Chmura: połączona")
                pull()
                onResult(true, "Zalogowano.")
            } else onResult(
                false,
                task.exception?.localizedMessage ?: "Nie udało się zalogować."
            )
        }
    }

    fun register(email: String, password: String, onResult: (Boolean, String) -> Unit) {
        val cleanEmail = email.trim()

        if (cleanEmail.isBlank()) {
            onResult(false, "Podaj adres e-mail.")
            return
        }

        if (password.isBlank()) {
            onResult(false, "Podaj hasło.")
            return
        }

        if (password.length < 6) {
            onResult(false, "Hasło musi mieć co najmniej 6 znaków.")
            return
        }

        val a = auth
        if (a == null) {
            onResult(false, "Firebase nie jest jeszcze gotowy.")
            return
        }

        try {
            a.createUserWithEmailAndPassword(cleanEmail, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        ready = true
                        store?.get()?.updateCloudStatus("Chmura: połączona")
                        pull()
                        onResult(true, "Konto utworzone.")
                    } else {
                        onResult(
                            false,
                            task.exception?.localizedMessage
                                ?: "Nie udało się utworzyć konta."
                        )
                    }
                }
        } catch (e: Exception) {
            onResult(false, e.localizedMessage ?: "Nie udało się utworzyć konta.")
        }
    }

    fun logout() {
        auth?.signOut()
        ready = false
        store?.get()?.updateCloudStatus("Chmura: wylogowano")
    }

    fun isConfigured() = auth != null && db != null
    fun isLoggedIn() = auth?.currentUser != null

    fun push(s: AppStore) {
        if (!ready) return
        val user = auth?.currentUser ?: return
        val root = db?.collection("wydatki")?.document(user.uid) ?: return
        val payload = hashMapOf<String, Any>(
            "data" to s.exportJson(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        root.set(payload).addOnSuccessListener { s.updateCloudStatus("Chmura: zapisano") }
            .addOnFailureListener { s.updateCloudStatus("Chmura: błąd zapisu") }
    }

    private fun pull() {
        val s = store?.get() ?: return
        val user = auth?.currentUser ?: return
        db?.collection("wydatki")?.document(user.uid)?.get()
            ?.addOnSuccessListener { doc ->
                val raw = doc.getString("data")
                if (!raw.isNullOrBlank()) s.importJsonFromCloud(raw)
                else push(s)
            }
            ?.addOnFailureListener { s.updateCloudStatus("Chmura: błąd odczytu") }
    }
}

@Composable
fun WydatkiApp(store: AppStore) {
    var screen by remember { mutableStateOf("dashboard") }
    var selected by remember { mutableStateOf<Category?>(null) }
    var month by remember { mutableStateOf(currentMonth()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val release = GithubUpdater.check()
        if (release != null) GithubUpdater.downloadAndInstall(context, release)
    }

    val selectedTab = when (screen) {
        "expenses" -> "expenses"
        "income" -> "income"
        "settings" -> "settings"
        else -> "dashboard"
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF6F8F7))) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) togetherWith
                            fadeOut(animationSpec = androidx.compose.animation.core.tween(120))
                },
                label = "screen_transition"
            ) { currentScreen ->
                when (currentScreen) {
                    "dashboard" -> DashboardScreen(
                        store, month,
                        { month = it },
                        { selected = it; screen = "category" },
                        { screen = it }
                    )

                    "category" -> selected?.let {
                        CategoryScreen(store, it, month) { screen = "dashboard" }
                    }

                    "expenses" -> ExpensesScreen(store, month) { screen = "dashboard" }
                    "income" -> IncomeScreen(store, month) { screen = "dashboard" }
                    "stats" -> StatisticsScreen(store, month, { month = it }) { screen = "dashboard" }
                    "search" -> SearchScreen(store) { screen = "dashboard" }
                    "settings" -> SettingsScreen(store) { screen = "dashboard" }
                }
            }
        }

        // Stała nawigacja — widoczna również wewnątrz Wydatków,
        // Dochodów, Statystyk, Wyszukiwania i Ustawień.
        BottomBar({ screen = it }, selectedTab)
    }
}

fun currentMonth() = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
fun monthOf(date: String) = date.take(7)
fun monthLabel(month: String): String {
    val names = listOf(
        "STYCZEŃ",
        "LUTY",
        "MARZEC",
        "KWIECIEŃ",
        "MAJ",
        "CZERWIEC",
        "LIPIEC",
        "SIERPIEŃ",
        "WRZESIEŃ",
        "PAŹDZIERNIK",
        "LISTOPAD",
        "GRUDZIEŃ"
    )
    return "${names[month.substring(5, 7).toInt() - 1]} ${month.substring(0, 4)}"
}

fun shiftMonth(month: String, d: Int): String {
    val c = Calendar.getInstance(); c.set(
        month.substring(0, 4).toInt(),
        month.substring(5, 7).toInt() - 1,
        1
    ); c.add(Calendar.MONTH, d)
    return SimpleDateFormat("yyyy-MM", Locale.US).format(c.time)
}

fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
fun money(v: Double) = String.format(Locale.forLanguageTag("pl-PL"), "%.2f zł", v)

@Composable
fun DashboardScreen(
    store: AppStore,
    month: String,
    setMonth: (String) -> Unit,
    category: (Category) -> Unit,
    tab: (String) -> Unit
) {
    val ex = store.expenses.filter { monthOf(it.date) == month }
    val inc = store.incomes.filter { monthOf(it.date) == month }
    val income = inc.sumOf { it.amount }
    val expenses = ex.sumOf { it.amount }
    Scaffold(
        containerColor = Color(0xFFF6F8F7)) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                MonthHeader(month, setMonth)
                SummarySection(income, expenses, income - expenses)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickButton("🔎", "Szukaj") { tab("search") }
                    QuickButton("📊", "Statystyki") { tab("stats") }
                }
                Spacer(Modifier.height(8.dp)); Text(
                "WYDATKI WG KATEGORII",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF455A64)
            )
            }
            items(store.categories) { c ->
                CategoryCard(
                    c,
                    ex.filter { it.categoryId == c.id }.sumOf { it.amount }) { category(c) }
            }
        }
    }
}

@Composable
fun androidx.compose.foundation.layout.RowScope.QuickButton(icon: String, text: String, onClick: () -> Unit) {
    Card(
        Modifier.weight(1f).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon); Spacer(Modifier.width(6.dp)); Text(
            text,
            fontWeight = FontWeight.SemiBold
        )
        }
    }
}

@Composable
fun MonthHeader(month: String, set: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton({ set(shiftMonth(month, -1)) }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Poprzedni"
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                monthLabel(month),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ); Text("Miesiąc", fontSize = 12.sp, color = Color.Gray)
        }
        IconButton({ set(shiftMonth(month, 1)) }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                "Następny"
            )
        }
    }
}

@Composable
fun SummarySection(income: Double, expenses: Double, remaining: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard(Modifier.weight(1f), "DOCHÓD", income, Color(0xFFE2F4E9), Color(0xFF087443))
        SummaryCard(
            Modifier.weight(1f),
            "WYDATKI",
            expenses,
            Color(0xFFFCE7E5),
            Color(0xFFC62828)
        )
    }
    Spacer(Modifier.height(8.dp)); SummaryCard(
        Modifier.fillMaxWidth(),
        "ZOSTAŁO",
        remaining,
        Color(0xFFE5F1F8),
        Color(0xFF156082)
    )
}

@Composable
fun SummaryCard(modifier: Modifier, title: String, amount: Double, bg: Color, fg: Color) {
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(bg)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg); Text(
            money(
                amount
            ), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = fg
        )
        }
    }
}

@Composable
fun CategoryCard(c: Category, amount: Double, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.emoji,
                    fontSize = 22.sp
                ); Spacer(Modifier.width(10.dp)); Text(c.name, Modifier.weight(1f)); Text(
                money(
                    amount
                ), fontWeight = FontWeight.SemiBold
            ); Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                "Otwórz",
                Modifier.size(15.dp),
                tint = Color.Gray
            )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(store: AppStore, c: Category, month: String, back: () -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    val list = store.expenses.filter { it.categoryId == c.id && monthOf(it.date) == month }
        .sortedByDescending { it.date }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${c.emoji}  ${c.name}") },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } })
        },
        floatingActionButton = {
            FloatingActionButton(
                { editing = null; dialog = true },
                containerColor = Color(0xFF278B68)
            ) { Icon(Icons.Default.Add, "Dodaj", tint = Color.White) }
        }) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p).padding(16.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SummaryCard(
                    Modifier.fillMaxWidth(),
                    "SUMA ${monthLabel(month)}",
                    list.sumOf { it.amount },
                    Color(0xFFE2F4E9),
                    Color(0xFF087443)
                ); Spacer(Modifier.height(8.dp))
            }
            if (list.isEmpty()) item { Empty("Brak wydatków w tej kategorii.") }
            items(list) { e ->
                ExpenseCard(e,
                    { editing = e; dialog = true },
                    { store.deleteExpense(e.id) })
            }
        }
    }
    if (dialog) ExpenseDialog(
        c,
        editing,
        {
            dialog = false
        }) {
        if (editing == null) store.addExpense(it) else store.updateExpense(it); dialog = false
    }
}

@Composable
fun ExpenseCard(e: Expense, edit: () -> Unit, delete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(Color.White)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    e.description.ifBlank { "Wydatek" },
                    fontWeight = FontWeight.SemiBold
                ); Text(
                e.date,
                fontSize = 12.sp,
                color = Color.Gray
            ); if (e.note.isNotBlank()) Text(e.note, fontSize = 12.sp, color = Color.Gray)
            }
            Text(
                money(e.amount),
                fontWeight = FontWeight.Bold
            ); IconButton(edit) {
            Icon(
                Icons.Default.Edit,
                "Edytuj"
            )
        }; IconButton(delete) { Icon(Icons.Default.Delete, "Usuń", tint = Color(0xFFC62828)) }
        }
    }
}

@Composable
fun ExpenseDialog(
    c: Category,
    initial: Expense?,
    dismiss: () -> Unit,
    save: (Expense) -> Unit
) {
    var amount by remember {
        mutableStateOf(
            initial?.amount?.toString()?.replace('.', ',') ?: ""
        )
    }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: today()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (initial == null) "Dodaj wydatek" else "Edytuj wydatek") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Kwota") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    desc,
                    { desc = it },
                    label = { Text("Opis") },
                    singleLine = true
                )
                OutlinedTextField(
                    date,
                    { date = it },
                    label = { Text("Data RRRR-MM-DD") },
                    singleLine = true
                )
                OutlinedTextField(
                    note,
                    { note = it },
                    label = { Text("Notatka") },
                    singleLine = true
                ); if (error.isNotBlank()) Text(error, color = Color.Red, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton({
                val n =
                    amount.replace(',', '.').toDoubleOrNull(); if (n == null || n <= 0) error =
                "Podaj poprawną kwotę." else if (desc.isBlank()) error =
                "Podaj opis." else save(
                Expense(
                    initial?.id ?: UUID.randomUUID().toString(),
                    c.id,
                    n,
                    desc.trim(),
                    date.trim(),
                    note.trim()
                )
            )
            }) { Text("ZAPISZ") }
        },
        dismissButton = { TextButton(dismiss) { Text("ANULUJ") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(store: AppStore, month: String, back: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    val list = store.expenses.filter {
        monthOf(it.date) == month && (query.isBlank() || it.description.contains(
            query,
            true
        ) || it.note.contains(query, true))
    }.sortedByDescending { it.date }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WYDATKI • ${monthLabel(month)}") },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } })
        },
        floatingActionButton = {
            FloatingActionButton(
                { editing = null; dialog = true },
                containerColor = Color(0xFF278B68)
            ) { Icon(Icons.Default.Add, "Dodaj", tint = Color.White) }
        }) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p).padding(16.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    query,
                    { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Szukaj") },
                    singleLine = true
                )
            }
            items(list) { e ->
                ExpenseCard(e,
                    { editing = e; dialog = true },
                    { store.deleteExpense(e.id) })
            }; if (list.isEmpty()) item { Empty("Brak wydatków.") }
        }
    }
    if (dialog) {
        val c = store.categories.firstOrNull { it.id == editing?.categoryId }
            ?: store.categories.first(); ExpenseDialog(
            c,
            editing,
            { dialog = false }) {
            if (editing == null) store.addExpense(it) else store.updateExpense(it); dialog =
            false
        }
    }
}

@Composable
fun StatisticsScreen(store: AppStore, month: String, setMonth: (String) -> Unit, back: () -> Unit) {
    var selectedYear by remember { mutableStateOf(month.substring(0, 4).toInt()) }
    LaunchedEffect(month) { selectedYear = month.substring(0, 4).toInt() }
    val ex = store.expenses.filter { monthOf(it.date).startsWith("$selectedYear-") }
    val income = store.incomes.filter { monthOf(it.date).startsWith("$selectedYear-") }.sumOf { it.amount }
    val total = ex.sumOf { it.amount }

    val selectedMonthExpenses = store.expenses.filter { monthOf(it.date) == month }
    val monthTotal = selectedMonthExpenses.sumOf { it.amount }
    val monthIncome = store.incomes.filter { monthOf(it.date) == month }.sumOf { it.amount }
    val grouped = store.categories.map { category ->
        category to selectedMonthExpenses.filter { it.categoryId == category.id }.sumOf { it.amount }
    }.filter { it.second > 0 }.sortedByDescending { it.second }
    val avg = if (selectedMonthExpenses.isEmpty()) 0.0
    else monthTotal / selectedMonthExpenses.map { it.date }.distinct().size

    Column(Modifier.fillMaxSize().background(Color(0xFFF6F8F7))) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") }
            Text(
                "STATYSTYKI",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ROK", fontWeight = FontWeight.Bold, color = Color(0xFF455A64))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton({ selectedYear-- }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Poprzedni rok")
                                }
                                Text(
                                    "$selectedYear",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton({ selectedYear++ }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, "Następny rok")
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text("SUMA ROKU", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            money(total),
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                        Text(
                            "Dochód: ${money(income)}  •  Bilans: ${money(income - total)}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            item {
                Text("MIESIĄCE • $selectedYear", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            items((1..12).toList()) { monthNumber ->
                val ym = String.format(Locale.US, "%04d-%02d", selectedYear, monthNumber)
                val mExpenses = store.expenses.filter { monthOf(it.date) == ym }.sumOf { it.amount }
                val mIncome = store.incomes.filter { monthOf(it.date) == ym }.sumOf { it.amount }
                val active = ym == month

                Card(
                    Modifier.fillMaxWidth().clickable { setMonth(ym) },
                    colors = CardDefaults.cardColors(if (active) Color(0xFFE8F4EE) else Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            monthLabel(ym).replace("$selectedYear", "").trim(),
                            Modifier.weight(1f),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(mExpenses), fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            if (mIncome > 0) Text(
                                "dochód ${money(mIncome)}",
                                fontSize = 11.sp,
                                color = Color(0xFF087443)
                            )
                        }
                    }
                }
            }

            item {
                Text("SZCZEGÓŁY • ${monthLabel(month)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            item {
                SummaryCard(
                    Modifier.fillMaxWidth(),
                    "WYDATKI",
                    monthTotal,
                    Color(0xFFFCE7E5),
                    Color(0xFFC62828)
                )
            }
            item {
                SummaryCard(
                    Modifier.fillMaxWidth(),
                    "ŚREDNIO / DZIEŃ",
                    avg,
                    Color(0xFFE5F1F8),
                    Color(0xFF156082)
                )
            }
            item {
                SummaryCard(
                    Modifier.fillMaxWidth(),
                    "DOCHÓD",
                    monthIncome,
                    Color(0xFFE2F4E9),
                    Color(0xFF087443)
                )
            }

            item {
                Text("PODZIAŁ WYDATKÓW", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            if (grouped.isNotEmpty()) {
                item {
                    ExpensePieChart(
                        grouped = grouped,
                        total = monthTotal
                    )
                }
                items(grouped) { (c, v) ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(Color.White)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row {
                                Text(
                                    "${c.emoji} ${c.name}",
                                    Modifier.weight(1f)
                                )
                                Text(money(v), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "${if (monthTotal > 0) (v / monthTotal * 100).toInt() else 0}% wszystkich wydatków",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                item { Empty("Brak wydatków w tym miesiącu.") }
            }
        }
    }
}

@Composable
fun SearchScreen(store: AppStore, back: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = store.expenses.filter {
        q.isNotBlank() && (it.description.contains(
            q,
            true
        ) || it.note.contains(
            q,
            true
        ) || store.categories.firstOrNull { c -> c.id == it.categoryId }?.name?.contains(
            q,
            true
        ) == true)
    }.sortedByDescending { it.date }
    Column(Modifier.fillMaxSize().background(Color(0xFFF6F8F7))) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") }
            Text(
                "WYSZUKIWANIE",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    q,
                    { q = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Szukaj w opisach, notatkach i kategoriach") },
                    singleLine = true
                )
            }
            if (q.isNotBlank()) item {
                Text(
                    "Znaleziono: ${list.size}",
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(list) { e -> ExpenseCard(e, {}, {}) }
            if (q.isNotBlank() && list.isEmpty()) item { Empty("Brak wyników.") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(store: AppStore, month: String, back: () -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Income?>(null) }
    val list = store.incomes.filter { monthOf(it.date) == month }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DOCHODY • ${monthLabel(month)}") },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") } })
        },
        floatingActionButton = {
            FloatingActionButton(
                { editing = null; dialog = true },
                containerColor = Color(0xFF278B68)
            ) { Icon(Icons.Default.Add, "Dodaj", tint = Color.White) }
        }) { p ->
        LazyColumn(
            Modifier.fillMaxSize().padding(p).padding(16.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SummaryCard(
                    Modifier.fillMaxWidth(),
                    "SUMA DOCHODÓW",
                    list.sumOf { it.amount },
                    Color(0xFFE2F4E9),
                    Color(0xFF087443)
                )
            }
            items(list) { i ->
                IncomeCard(i,
                    { editing = i; dialog = true },
                    { store.deleteIncome(i.id) })
            }; if (list.isEmpty()) item { Empty("Brak dochodów.") }
        }
    }
    if (dialog) IncomeDialog(editing,
        { dialog = false }) {
        if (editing == null) store.addIncome(it) else store.updateIncome(
            it
        ); dialog = false
    }
}

@Composable
fun IncomeCard(i: Income, edit: () -> Unit, delete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    i.source,
                    fontWeight = FontWeight.SemiBold
                ); Text(i.date, fontSize = 12.sp, color = Color.Gray)
            }; Text(
            money(i.amount),
            fontWeight = FontWeight.Bold
        ); IconButton(edit) {
            Icon(
                Icons.Default.Edit,
                "Edytuj"
            )
        }; IconButton(delete) { Icon(Icons.Default.Delete, "Usuń", tint = Color.Red) }
        }
    }
}

@Composable
fun IncomeDialog(initial: Income?, dismiss: () -> Unit, save: (Income) -> Unit) {
    var source by remember { mutableStateOf(initial?.source ?: "") }
    var amount by remember {
        mutableStateOf(
            initial?.amount?.toString()?.replace('.', ',') ?: ""
        )
    }
    var date by remember { mutableStateOf(initial?.date ?: today()) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (initial == null) "Dodaj dochód" else "Edytuj dochód") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    source,
                    { source = it },
                    label = { Text("Źródło") },
                    singleLine = true
                ); OutlinedTextField(
                amount,
                { amount = it },
                label = { Text("Kwota") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            ); OutlinedTextField(
                date,
                { date = it },
                label = { Text("Data RRRR-MM-DD") },
                singleLine = true
            ); if (error.isNotBlank()) Text(error, color = Color.Red)
            }
        },
        confirmButton = {
            TextButton({
                val n = amount.replace(',', '.').toDoubleOrNull(); if (source.isBlank()) error =
                "Podaj źródło." else if (n == null || n <= 0) error =
                "Podaj poprawną kwotę." else save(
                Income(
                    initial?.id ?: UUID.randomUUID().toString(), source.trim(), n, date.trim()
                )
            )
            }) { Text("ZAPISZ") }
        },
        dismissButton = { TextButton(dismiss) { Text("ANULUJ") } })
}


@Composable
fun SettingsScreen(store: AppStore, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var categoryDialog by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Category?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var updateChecking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }

    val create =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openOutputStream(uri)
                        ?.use { it.write(store.exportJson().toByteArray()) }
                    message = "Kopia została zapisana."
                } catch (_: Exception) {
                    message = "Nie udało się zapisać kopii."
                }
            }
        }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use { it.readText() } ?: ""
                    message =
                        if (store.importJson(json)) "Kopia została przywrócona." else "Nieprawidłowy plik kopii."
                } catch (_: Exception) {
                    message = "Nie udało się odczytać kopii."
                }
            }
        }

    Column(Modifier.fillMaxSize().background(Color(0xFFF6F8F7))) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć") }
            Text(
                "USTAWIENIA",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "☁️ Synchronizacja z chmurą",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(store.cloudStatus, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        if (FirebaseCloud.isLoggedIn()) {
                            Text(
                                "Konto jest zalogowane. Dane są synchronizowane z Firestore.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { FirebaseCloud.logout() }) { Text("WYLOGUJ") }
                        } else {
                            Text(
                                "Aby odzyskać dane po usunięciu aplikacji, zaloguj się do konta Firebase.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                email,
                                { email = it },
                                label = { Text("E-mail") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                password,
                                { password = it },
                                label = { Text("Hasło") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    FirebaseCloud.login(email, password) { ok, text ->
                                        message = text
                                    }
                                }) { Text("ZALOGUJ") }
                                Button(onClick = {
                                    FirebaseCloud.register(
                                        email,
                                        password
                                    ) { ok, text -> message = text }
                                }) { Text("UTWÓRZ KONTO") }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Kopia zapasowa", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Dodatkowa kopia wszystkich danych w pliku JSON.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ create.launch("Wydatki-backup.json") }) { Text("EKSPORTUJ") }
                            Button({
                                open.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/*"
                                    )
                                )
                            }) { Text("IMPORTUJ") }
                        }
                        if (message.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(message, fontSize = 12.sp)
                        }
                    }
                }
            }

            item { Text("Kategorie", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            item {
                Button({ edit = null; categoryDialog = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(5.dp))
                    Text("DODAJ KATEGORIĘ")
                }
            }
            items(store.categories) { c ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White)) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c.emoji, fontSize = 23.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(c.name, Modifier.weight(1f))
                        IconButton({
                            edit = c; categoryDialog = true
                        }) { Icon(Icons.Default.Edit, "Edytuj") }
                        IconButton({ store.deleteCategory(c.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                "Usuń",
                                tint = Color.Red
                            )
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Aktualizacje", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Sprawdzanie nowych wersji z GitHub Releases.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(enabled = !updateChecking, onClick = {
                            updateChecking = true
                            updateMessage = "Sprawdzanie…"
                            scope.launch {
                                val release = GithubUpdater.check()
                                updateChecking = false
                                if (release == null) updateMessage =
                                    "Brak nowszej wersji lub nie skonfigurowano GitHub."
                                else {
                                    updateMessage = "Dostępna wersja ${release.tag}."
                                    GithubUpdater.downloadAndInstall(context, release)
                                }
                            }
                        }) {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(5.dp))
                            Text(if (updateChecking) "SPRAWDZANIE…" else "SPRAWDŹ AKTUALIZACJĘ")
                        }
                        if (updateMessage.isNotBlank()) Text(updateMessage, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (categoryDialog) {
        CategoryDialog(edit, { categoryDialog = false }) {
            if (edit == null) store.addCategory(it) else store.updateCategory(it)
            categoryDialog = false
        }
    }
}

@Composable
fun CategoryDialog(initial: Category?, dismiss: () -> Unit, save: (Category) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "💰") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (initial == null) "Dodaj kategorię" else "Edytuj kategorię") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Nazwa") },
                    singleLine = true
                ); OutlinedTextField(
                emoji,
                { emoji = it },
                label = { Text("Emoji") },
                singleLine = true
            )
            }
        },
        confirmButton = {
            TextButton({
                if (name.isNotBlank()) save(
                    Category(
                        initial?.id ?: UUID.randomUUID().toString(),
                        name.trim(),
                        emoji.ifBlank { "💰" },
                        initial?.sort ?: 999
                    )
                )
            }) { Text("ZAPISZ") }
        },
        dismissButton = { TextButton(dismiss) { Text("ANULUJ") } })
}

@Composable
fun BottomBar(tab: (String) -> Unit, selected: String) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomItem("🏠", "Pulpit", selected == "dashboard") { tab("dashboard") }
        BottomItem("💸", "Wydatki", selected == "expenses") { tab("expenses") }
        BottomItem("💰", "Dochody", selected == "income") { tab("income") }
        BottomItem("⚙️", "Ustawienia", selected == "settings") { tab("settings") }
    }
}

@Composable
fun BottomItem(e: String, t: String, s: Boolean, on: () -> Unit) {
    val iconSize by animateDpAsState(
        targetValue = if (s) 24.dp else 20.dp,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "bottom_icon_size"
    )
    val textColor by animateColorAsState(
        targetValue = if (s) Color(0xFF278B68) else Color.Gray,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "bottom_text_color"
    )
    Column(
        Modifier.clickable(onClick = on).padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(e, fontSize = iconSize.value.sp)
        Text(
            t,
            fontSize = 11.sp,
            color = textColor,
            fontWeight = if (s) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ExpensePieChart(grouped: List<Pair<Category, Double>>, total: Double) {
    val colors = listOf(
        Color(0xFF278B68), Color(0xFFC62828), Color(0xFF156082), Color(0xFFF9A825),
        Color(0xFF6A1B9A), Color(0xFFAD1457), Color(0xFF2E7D32), Color(0xFFEF6C00)
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            var startAngle = -90f
            grouped.forEachIndexed { index, pair ->
                val sweepAngle = (pair.second / total * 360f).toFloat()
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 30.dp.toPx())
                )
                startAngle += sweepAngle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Wydatki", fontSize = 12.sp, color = Color.Gray)
            Text(money(total), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun Empty(text: String) {
    Box(
        Modifier.fillMaxWidth().padding(30.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.Gray) }
}


object GithubUpdater {
    // Ustawienia aktualizacji z GitHub Releases
    private const val GITHUB_OWNER = "janczesko12"
    private const val GITHUB_REPO = "Wydatki"
    private const val CURRENT_VERSION = "1.0.0"

    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        if (GITHUB_OWNER == "YOUR_GITHUB_USERNAME") return@withContext null
        try {
            val c =
                URL("https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 7000
            c.readTimeout = 7000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.getString("tag_name").removePrefix("v")
            if (!isNewer(tag, CURRENT_VERSION)) return@withContext null
            val assets = o.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", true)) return@withContext ReleaseInfo(
                    tag,
                    a.getString("browser_download_url")
                )
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewer(a: String, b: String): Boolean {
        fun parts(v: String) = v.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            .let { it + List(max(0, 3 - it.size)) { 0 } }

        val x = parts(a)
        val y = parts(b)
        for (i in 0..2) if (x[i] != y[i]) return x[i] > y[i]
        return false
    }

    suspend fun downloadAndInstall(context: Context, release: ReleaseInfo) =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, "Wydatki-${release.tag}.apk")
                URL(release.apkUrl).openStream()
                    .use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(intent)
                }
            } catch (_: Exception) {
            }
        }
}