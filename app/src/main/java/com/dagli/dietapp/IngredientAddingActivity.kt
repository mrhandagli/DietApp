package com.dagli.dietapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * IngredientAddingActivity, bir öğünün içeriğine (ingredient) ait besinleri
 * seçerek kaydetmeyi ve bunlardan tarif oluşturmayı sağlayan aktivitedir.
 */
class IngredientAddingActivity : ComponentActivity() {

    /**
     * Aktivite oluşturulduğunda çağrılır.
     *
     * @param savedInstanceState Eğer aktivite yeniden yaratılırsa (ekran döndürme vb.),
     * önceki durum bu parametre ile alınabilir.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mealId = intent.getIntExtra("MEAL_ID", -1)
        val app = application as DietApp

        setContent {
            MaterialTheme {
                var loading by remember { mutableStateOf(true) }
                var currentMeal by remember { mutableStateOf<Meal?>(null) }
                var mealNutrientDistribution by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
                var foods by remember { mutableStateOf<List<Food>>(emptyList()) }
                var savedJournalEntry by remember { mutableStateOf<FoodJournalEntry?>(null) }

                LaunchedEffect(true) {
                    // Arka plan (IO) işlemlerini bir coroutine içerisinde yap
                    lifecycleScope.launch(Dispatchers.IO) {
                        // Bu öğünle ilgili kayıt çekme
                        val mealDao = app.db.mealDao()
                        currentMeal = mealDao.getMealById(mealId)

                        // NutrientDistribution'ı SharedPreferences'tan yükleme
                        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                        val distributionJson = sharedPref.getString("NutrientDistribution", "{}")
                        val finalDist = loadDistributionFromJson(distributionJson!!)
                        val thisMealDist = finalDist[mealId] ?: emptyMap()

                        // Eğer veritabanında besin (Food) kayıtları yoksa
                        // assets klasöründen bir JSON dosyası yükleyip ekleme
                        val foodDao = app.db.foodDao()
                        if (foodDao.getAllFoods().isEmpty()) {
                            val initialFoods = loadFoodsFromJson(this@IngredientAddingActivity)
                            if (initialFoods.isNotEmpty()) {
                                foodDao.insertFoods(initialFoods)
                            }
                        }

                        // Yüklenen besinleri hafızaya al ve FoodStore'a aktar
                        foods = foodDao.getAllFoods()
                        FoodStore.initialize(foods)

                        // Bu öğüne ait besin dağılımını sakla
                        mealNutrientDistribution = thisMealDist

                        // FoodJournalEntry veritabanından (mevcutsa) yükle
                        val journalDao = app.db.foodJournalDao()
                        val existingEntry = journalDao.getEntryForMealAndDate(mealId, LocalDateTime.now())
                        savedJournalEntry = existingEntry
                    }
                    // Yükleme işlemi tamamlandığında UI'ı güncelle
                    loading = false
                }

                // Yükleme sürecinde göstereceğimiz ekran
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Eğer mevcut bir meal (öğün) yoksa uyarı göster
                    currentMeal?.let { meal ->
                        IngredientAddingScreen(
                            meal = meal,
                            mealDistribution = mealNutrientDistribution,
                            foods = foods,
                            savedJournalEntry = savedJournalEntry,
                            onSave = { selectedIngredients, totalCalories ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    // Mevcut entry varsa güncelle, yoksa ekle
                                    val journalDao = app.db.foodJournalDao()
                                    val existingEntry = journalDao.getEntryForMealAndDate(meal.id, LocalDateTime.now())
                                    val updatedEntry = FoodJournalEntry(
                                        entryId = existingEntry?.entryId ?: 0, // Güncellemede eski ID'yi kullan
                                        mealId = meal.id,
                                        mealName = meal.name,
                                        dateTime = LocalDateTime.now(),
                                        ingredients = selectedIngredients,
                                        totalCalories = totalCalories
                                    )
                                    if (existingEntry != null) {
                                        journalDao.updateEntry(updatedEntry)
                                    } else {
                                        journalDao.insertEntry(updatedEntry)
                                    }
                                    withContext(Dispatchers.Main) {
                                        finish()
                                    }
                                }
                            },
                            onBackClick = { finish() },
                            onGenerateRecipe = { selectedIngredients ->
                                // Kullanıcının seçtiği malzemeleri RecipeGenerationActivity'ye gönder
                                val selectedJson = Gson().toJson(selectedIngredients)
                                val intent = Intent(
                                    this@IngredientAddingActivity,
                                    RecipeGenerationActivity::class.java
                                )
                                intent.putExtra("SELECTED_INGREDIENTS", selectedJson)
                                startActivity(intent)
                            }
                        )
                    } ?: run {
                        Text("Öğün bulunamadı.")
                    }
                }
            }
        }
    }

    /**
     * Besin dağılımını JSON formatından yükler.
     *
     * @param json JSON verisini temsil eden string.
     * @return Map<Int, Map<String, Double>> şeklinde öğün ID -> (besin kategorisi -> miktar).
     */
    private fun loadDistributionFromJson(json: String): Map<Int, Map<String, Double>> {
        val type = object : TypeToken<Map<Int, Map<String, Double>>>() {}.type
        return try {
            Gson().fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Assets klasöründeki `foods.json` dosyasından besin bilgilerini yükler.
     *
     * @param context Aktif [Context] örneği, assets klasörüne erişmek için kullanılır.
     * @return Besin (`Food`) nesnelerinin listesi.
     */
    private fun loadFoodsFromJson(context: Context): List<Food> {
        return try {
            val jsonString = context.assets.open("foods.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<FoodsWrapper>() {}.type
            val wrapper: FoodsWrapper = Gson().fromJson(jsonString, type)
            wrapper.foodExchangeTable
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}


/**
 * HTTP üzerinden akıllı tartı cihazından (smartscale.local) ağırlık verisi okur.
 *
 * @return Okunan ağırlığı Double olarak döndürür; bağlantı hatası olursa `null`.
 */
suspend fun readScaleValue(): Double? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("http://smartscale.local/value")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().use { reader ->
                val line = reader.readLine()
                line.trim().toDoubleOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Basit bir yardımcı fonksiyon: json içinde yer alan "raw", "cooked", "neutral" gibi değerleri
 * "Çiğ", "Pişmiş", "Nötr" gibi Türkçe etiketlere dönüştürür.
 *
 * @param state Orijinal state değeri ("raw", "cooked", "neutral").
 * @return Kullanıcıya gösterilecek Türkçe etiket.
 */
fun mapStateToTurkish(state: String): String {
    return when (state) {
        "raw" -> "Çiğ"
        "cooked" -> "Pişmiş"
        "neutral" -> "~"
        else -> "Bilinmiyor"
    }
}

/**
 * IngredientAddingScreen, kullanıcıya öğündeki besin kategorilerini ve besinleri
 * göstererek seçim yapmalarına, seçimlerini düzenlemelerine ve kaydetmelerine
 * olanak tanıyan bir Compose fonksiyonudur.
 *
 * @param meal Kullanıcının düzenlediği öğün.
 * @param mealDistribution Bu öğüne ait besin kategorisi -> miktar bilgisi.
 * @param foods Veritabanındaki tüm `Food` nesneleri.
 * @param savedJournalEntry Eğer daha önce bu öğüne ait bir entry oluşturulmuşsa, o veriyi içerir.
 * @param onSave Seçili malzemeler (ingredients) ve toplam kalori kaydedileceğinde çağrılır.
 * @param onBackClick Geri tuşuna basıldığında veya BackHandler devreye girdiğinde çağrılan fonksiyon.
 * @param onGenerateRecipe Tarif oluşturma işlemi için seçili malzemeleri alan callback fonksiyonu.
 */
@Composable
fun IngredientAddingScreen(
    meal: Meal,
    mealDistribution: Map<String, Double>,
    foods: List<Food>,
    savedJournalEntry: FoodJournalEntry?,
    onSave: (List<SelectedIngredient>, Double) -> Unit,
    onBackClick: () -> Unit,
    onGenerateRecipe: (List<SelectedIngredient>) -> Unit
) {
    val categories = mealDistribution.filter { it.value > 0.0 }
    val foodsByCategory = remember(foods) { foods.groupBy { it.group } }
    val selectedIngredients = remember { mutableStateListOf<SelectedIngredient>() }

    // Eğer bir FoodJournalEntry kayıtlı ise, içeriğini seçili malzemelere ekle
    LaunchedEffect(savedJournalEntry) {
        savedJournalEntry?.ingredients?.let {
            selectedIngredients.addAll(it)
        }
    }

    // Donanım Geri Tuşunu (Back button) yakalayarak, onBackClick fonksiyonunu çağırır
    BackHandler {
        onBackClick()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    /**
     * Bir kategori için seçili olan toplam değeri döndürür.
     * @param category Besin kategorisi.
     * @return Bu kategori için seçilen toplam miktar (örneğin değişim sayısı).
     */
    fun totalChosenForCategory(category: String): Double {
        return selectedIngredients.filter { it.group == category }.sumOf { it.amount }
    }

    // Seçili malzemelerin toplam kalorisini hesapla
    val totalCalories by remember(selectedIngredients) {
        derivedStateOf {
            selectedIngredients.sumOf { selectedIngredient ->
                CalorieCalculator.computeCaloriesForCategory(
                    selectedIngredient.group,
                    selectedIngredient.amount
                )
            }
        }
    }

    // Kaydet butonunun aktif olup olmayacağını belirler
    val canSave = categories.isNotEmpty() && categories.all { (cat, allowed) ->
        totalChosenForCategory(cat) == allowed
    }

    var showSearch by remember { mutableStateOf(false) }
    var showSelected by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }

    val categoriesList = categories.keys.toList()
    val isSearching = showSearch && searchQuery.isNotBlank()

    // Sadece eşleşen (search) kategorileri görüntülemek için
    val categoriesWithMatches = remember(searchQuery, isSearching, foodsByCategory) {
        if (!isSearching) {
            // Arama yoksa tüm kategoriler gösterilsin
            categoriesList.associateWith { true }
        } else {
            // Arama varsa, kategori içinde searchQuery içeren besin varsa true
            categoriesList.associateWith { category ->
                val catFoods = foodsByCategory[category].orEmpty()
                catFoods.any { it.name.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text("Öğün: ${meal.name}", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))

            if (categories.isEmpty()) {
                // Eğer besin dağılımı yoksa bilgi göster
                Text("Bu öğün için değişim belirlenmemiş.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { showSelected = !showSelected }) {
                        Text(if (showSelected) "Seçilenler ↓" else "Seçilenler ↑")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!canSave) {
                        Text(
                            "Kaydetmek için hepsini doldur",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Toplam Kalori Göstergesi
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Toplam Kalori: $totalCalories kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Arama kutusu
                if (showSearch) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Besin Ara") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Kategori ve besin listesi
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(categoriesList) { category ->
                        val allowed = mealDistribution[category] ?: 0.0
                        val chosen = totalChosenForCategory(category)
                        val hasMatches = categoriesWithMatches[category] == true

                        if (isSearching && !hasMatches) return@items  // Arama eşleşmesi yoksa geç

                        val isExpanded = if (isSearching) {
                            hasMatches // Arama varsa eşleşme olan kategoriyi direk aç
                        } else {
                            expandedCategories.contains(category)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$category: ${formatNumber(chosen)}/${formatNumber(allowed)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!isSearching) {
                                    Button(onClick = {
                                        expandedCategories =
                                            if (isExpanded) expandedCategories - category
                                            else expandedCategories + category
                                    }) {
                                        Text(if (isExpanded) "Gizle" else "Besinler")
                                    }
                                }
                            }

                            // Kategori genişletilmişse besinleri göster
                            if (isExpanded) {
                                val catFoods = foodsByCategory[category].orEmpty().filter {
                                    if (isSearching && searchQuery.isNotBlank()) {
                                        it.name.contains(searchQuery, ignoreCase = true)
                                    } else true
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                if (catFoods.isEmpty()) {
                                    Text("Eşleşme yok.")
                                } else {
                                    catFoods.forEach { food ->
                                        val isSelected =
                                            selectedIngredients.any { it.foodId == food.id && it.group == food.group }
                                        val allowedCat = mealDistribution[category] ?: 0.0
                                        val remainingCat = allowedCat - totalChosenForCategory(category)

                                        IngredientItem(
                                            food = food,
                                            isSelected = isSelected,
                                            remaining = remainingCat,
                                            onSelect = { ingredient ->
                                                val currentTotal = totalChosenForCategory(ingredient.group)
                                                val existing = selectedIngredients.find {
                                                    it.foodId == ingredient.foodId && it.group == ingredient.group
                                                }

                                                // Aynı besin seçiliyse kaldır, değilse ekle
                                                if (existing != null) {
                                                    selectedIngredients.remove(existing)
                                                } else {
                                                    // Seçilen besin kalan limiti aşmıyorsa ekle
                                                    if (currentTotal + ingredient.amount <= allowedCat) {
                                                        selectedIngredients.add(ingredient)
                                                    } else {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("${ingredient.group} limitini aşamazsınız")
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Seçilen besinler bölümü
                if (showSelected) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Seçilenler:", style = MaterialTheme.typography.titleMedium)
                    if (selectedIngredients.isEmpty()) {
                        Text("Boş")
                    } else {
                        selectedIngredients.groupBy { it.group }.forEach { (cat, ingList) ->
                            Spacer(modifier = Modifier.height(8.dp))
                            val allowed = mealDistribution[cat] ?: 0.0
                            val chosen = totalChosenForCategory(cat)
                            Text(
                                "$cat: ${formatNumber(chosen)}/${formatNumber(allowed)}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            ingList.forEach { selectedIng ->
                                SelectedIngredientRow(
                                    selectedIng = selectedIng,
                                    mealDistribution = mealDistribution,
                                    totalChosenForCategory = { c -> totalChosenForCategory(c) },
                                    onUpdate = { updated ->
                                        val idx = selectedIngredients.indexOf(selectedIng)
                                        if (idx != -1) {
                                            selectedIngredients[idx] = updated
                                        }
                                    },
                                    onRemove = { selectedIngredients.remove(selectedIng) },
                                    showError = { msg ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                // KAYDET butonu
                Button(
                    onClick = { onSave(selectedIngredients.toList(), totalCalories.toDouble()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave
                ) {
                    Text("Kaydet")
                }

                Spacer(modifier = Modifier.height(8.dp))
                // TARİF OLUŞTUR butonu
                Button(
                    onClick = { onGenerateRecipe(selectedIngredients.toList()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave
                ) {
                    Text("Tarif Oluştur")
                }
            }
        }
    }
}

/**
 * Seçilen her malzeme (ingredient) için tek satır gösterimi.
 *
 * @param selectedIng Seçili ingredient nesnesi.
 * @param mealDistribution Öğün için belirlenen maksimum besin kategorisi dağılımını tutan harita.
 * @param totalChosenForCategory Her kategori için şu ana kadar seçilmiş miktarı döndüren fonksiyon.
 * @param onUpdate Kullanıcının giriş yaptığı (arttı/azalttı vb.) güncel ingredient verisiyle çağrılır.
 * @param onRemove Malzeme seçiminin tamamen kaldırılması için çağrılır.
 * @param showError Hata mesajlarını göstermek için Snackbar gibi mekanizmayı devreye sokar.
 */
@Composable
fun SelectedIngredientRow(
    selectedIng: SelectedIngredient,
    mealDistribution: Map<String, Double>,
    totalChosenForCategory: (String) -> Double,
    onUpdate: (SelectedIngredient) -> Unit,
    onRemove: () -> Unit,
    showError: (String) -> Unit
) {
    var showMeasurementMenu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // Bu kategoride kaç değişim (exchange) kullanılabilir
    val allowed = mealDistribution[selectedIng.group] ?: 0.0
    // Seçilen malzemenin gramaj (baseDisplay) cinsinden değeri
    val baseDisplay = selectedIng.amount * selectedIng.baseAmount

    // Türkçe durum metni (harici fonksiyonla dönüştürülür)
    val stateLabel = mapStateToTurkish(selectedIng.state)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    val scaleEnabled = sharedPref.getBoolean("scale_enabled", false)

    // Artış/azalış adım seçenekleri
    val increments = when {
        allowed < 1.0 -> listOf(0.5, 0.25, 0.1, 0.05)
        else -> listOf(1.0, 0.5, 0.25, 0.1, 0.05)
    }
    var increment by remember { mutableStateOf(increments.first()) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Uzun basıldığında akıllı tartıdan veri okunabilir (eğer etkinse)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        if (scaleEnabled && selectedIng.unit == "gram") {
                            coroutineScope.launch {
                                val weightFromScale = readScaleValue()
                                if (weightFromScale == null) {
                                    showError("Tartıdan veri alınamadı. Bağlantıyı kontrol edin.")
                                } else {
                                    if (weightFromScale <= 0.0) {
                                        // Eğer ağırlık 0 ise malzemeyi kaldır
                                        onRemove()
                                        showError("Tartıda ölçülen değer 0. Besin kaldırıldı.")
                                    } else {
                                        val exchanges = weightFromScale / selectedIng.baseAmount
                                        val rounded = roundToIncrement(exchanges, increment, 2)

                                        val currentTotal = totalChosenForCategory(selectedIng.group) - selectedIng.amount
                                        val maxAllowedForThis = allowed - currentTotal
                                        val finalExchanges =
                                            if (rounded <= maxAllowedForThis) rounded else maxAllowedForThis

                                        onUpdate(selectedIng.copy(amount = finalExchanges))
                                        showError(
                                            "Tartıdan alınan değer: " +
                                                    "${formatNumber(weightFromScale)} g, " +
                                                    "${formatNumber(finalExchanges)} değişim belirlendi."
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
            .padding(vertical = 4.dp)
    ) {
        // Seçilmiş öğenin bilgisi - Türkçe durum etiketi kullanılır
        Text(
            "${selectedIng.name} ($stateLabel) ${formatNumber(baseDisplay)} ${selectedIng.unit}",
            modifier = Modifier
                .weight(1f)
                .clickable { showMeasurementMenu = true }
        )

        // Artış miktarı seçim kutusu (dropdown)
        Box {
            Text(
                text = "Art: ${formatNumber(increment)}",
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                increments.forEach { value ->
                    DropdownMenuItem(
                        onClick = {
                            increment = value
                            expanded = false
                        },
                        text = { Text(formatNumber(value)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // '-' Butonu (azalt)
        IconButton(onClick = {
            val newAmount = selectedIng.amount - increment
            if (newAmount <= 0) {
                onRemove()
            } else {
                onUpdate(selectedIng.copy(amount = newAmount))
            }
        }) {
            Text("-")
        }

        // Seçili miktarı göster
        Text(
            formatNumber(selectedIng.amount),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // '+' Butonu (arttır)
        IconButton(onClick = {
            val currentTotal = totalChosenForCategory(selectedIng.group)
            val incremented = selectedIng.amount + increment
            val diff = incremented - selectedIng.amount
            if (currentTotal + diff <= allowed) {
                onUpdate(selectedIng.copy(amount = incremented))
            } else {
                showError("${selectedIng.group} limitini aşamazsınız")
            }
        }) {
            Text("+")
        }

        // Ölçüm değiştirme menüsü (Çiğ / Pişmiş / Nötr vb.)
        if (showMeasurementMenu) {
            MeasurementMenu(
                foodId = selectedIng.foodId,
                currentState = selectedIng.state,
                currentUnit = selectedIng.unit,
                onSelected = { newState, newUnit, newBaseAmount ->
                    onUpdate(selectedIng.copy(state = newState, unit = newUnit, baseAmount = newBaseAmount))
                    showMeasurementMenu = false
                },
                onDismiss = { showMeasurementMenu = false }
            )
        }
    }
}

/**
 * Bir değeri, belirtilen artış miktarına (increment) yuvarlar.
 *
 * @param value Yuvarlanacak değer.
 * @param increment Her adımda artış/azalış miktarı.
 * @param precision Yuvarlama için ondalık basamak sayısı.
 * @return Yuvarlanmış değer.
 */
fun roundToIncrement(value: Double, increment: Double, precision: Int = 2): Double {
    val steps = (value / increment).roundToInt()
    val roundedValue = steps * increment
    return String.format("%.${precision}f", roundedValue).toDouble()
}

/**
 * Kullanıcının seçtiği malzemenin çiğ, pişmiş vb. ölçüm seçeneklerini değiştirebileceği menü.
 *
 * @param foodId Düzenlenecek `Food` nesnesinin kimliği.
 * @param currentState Mevcut durum ("raw", "cooked", veya "neutral").
 * @param currentUnit Mevcut birim (örn. "gram").
 * @param onSelected Yeni ölçüm durumu ve birimi seçildiğinde çağrılır.
 * @param onDismiss Menü kapatılmak istendiğinde çağrılır.
 */
@Composable
fun MeasurementMenu(
    foodId: Int,
    currentState: String,
    currentUnit: String,
    onSelected: (String, String, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val food = FoodStore.getFoodById(foodId)

    // Kullanılabilir durumlar (çiğ, pişmiş, nötr) ölçümler varsa listelenir
    val states = listOf("raw", "cooked", "neutral").filter { state ->
        when (state) {
            "raw" -> food.measurements.raw?.net?.isNotEmpty() == true || food.measurements.raw?.gross?.isNotEmpty() == true
            "cooked" -> food.measurements.cooked?.net?.isNotEmpty() == true || food.measurements.cooked?.gross?.isNotEmpty() == true
            "neutral" -> food.measurements.neutral?.net?.isNotEmpty() == true || food.measurements.neutral?.gross?.isNotEmpty() == true
            else -> false
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        Column {
            Text("Ölçümü Değiştir", style = MaterialTheme.typography.titleMedium)
            states.forEach { state ->
                val detail = when (state) {
                    "raw" -> food.measurements.raw
                    "cooked" -> food.measurements.cooked
                    else -> food.measurements.neutral
                }
                val units = (detail?.net.orEmpty() + detail?.gross.orEmpty())
                units.forEach { m ->
                    val unit = m.unit
                    val amount = m.amount
                    // Kullanıcıya Türkçe gösteriyoruz ama onSelected "raw", "cooked", "neutral" gibi orijinalleri gönderiyor
                    val displayLabel = mapStateToTurkish(state)
                    Text(
                        "$displayLabel - $unit = ${formatNumber(amount)}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Burada "raw"/"cooked"/"neutral" geri dönüyor,
                                // veritabanı ve JSON'da orijinal değerler saklanıyor
                                onSelected(state, unit, amount)
                            }
                            .padding(4.dp)
                    )
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("İptal")
            }
        }
    }
}


/**
 * FoodStore bellekteki `Food` nesnelerinin hızlı erişimini sağlayan bir yardımcı nesnedir.
 */
object FoodStore {
    private var foods: List<Food> = emptyList()

    /**
     * Food listesini ilkeler. Uygulama başlatıldığında veya veriler yenilendiğinde çağrılmalıdır.
     * @param list Veritabanından veya JSON'dan gelen `Food` nesnelerinin listesi.
     */
    fun initialize(list: List<Food>) {
        foods = list
    }

    /**
     * Kimliği (id) verilen bir `Food` nesnesini geri döndürür.
     * @param id Aranan besinin kimliği.
     * @return İlgili `Food` nesnesi.
     */
    fun getFoodById(id: Int): Food {
        return foods.first { it.id == id }
    }
}

/**
 * Bir liste öğesi olarak kullanılan IngredientItem, belirli bir `Food` nesnesini
 * seçilebilir (clickable) şekilde gösterir.
 *
 * @param food Gösterilecek `Food` nesnesi.
 * @param isSelected Eğer true ise, kullanıcı bu besini seçmiş demektir.
 * @param remaining Bu kategoride (group) kaç değişim (exchange) daha seçilebileceğini gösterir.
 * @param onSelect Kullanıcı bu öğeyi seçtiğinde çağrılır; seçili `SelectedIngredient` döndürülür.
 */
@Composable
fun IngredientItem(
    food: Food,
    isSelected: Boolean,
    remaining: Double,
    onSelect: (SelectedIngredient) -> Unit
) {
    val defaultMeasurement = food.defaultMeasurement
    val detail = when (defaultMeasurement.state) {
        "raw" -> food.measurements.raw
        "cooked" -> food.measurements.cooked
        else -> food.measurements.neutral
    }

    // Kullanıcı tarafından belirlenen varsayılan ölçüm (unit) bulunmaya çalışılır
    val exactMeasurementDetail = (detail?.net.orEmpty() + detail?.gross.orEmpty())
        .find { it.unit == defaultMeasurement.unit }

    val fallbackMeasurementDetail = exactMeasurementDetail ?: run {
        val allMeasurements = detail?.net.orEmpty() + detail?.gross.orEmpty()
        allMeasurements.firstOrNull()
    }

    // Eğer hiçbir ölçüm detayı yoksa bu item'ı gösterme
    if (fallbackMeasurementDetail == null) {
        return
    }

    val chosenUnit = fallbackMeasurementDetail.unit
    val chosenAmountPerExchange = fallbackMeasurementDetail.amount

    // Seçim esnasında ilk eklenen miktarı belirle
    val initialExchange = when {
        remaining <= 0.0 -> 0.0
        remaining >= 1.0 -> 1.0
        remaining >= 0.5 -> 0.5
        remaining >= 0.25 -> 0.25
        remaining >= 0.1 -> 0.1
        else -> 0.05
    }

    val displayAmount = if (remaining <= 0.0) "-" else formatNumber(chosenAmountPerExchange * initialExchange)
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val isDisabled = remaining <= 0.0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .let {
                // Eğer seçilemez (örnek: limit dolmuş) ise tıklanmasın
                if (!isDisabled) it.clickable {
                    onSelect(
                        SelectedIngredient(
                            foodId = food.id,
                            name = food.name,
                            group = food.group,
                            // Orijinal state: "raw", "cooked", "neutral"
                            // UI'da mapStateToTurkish ile dönüştürüyoruz
                            state = defaultMeasurement.state,
                            unit = chosenUnit,
                            amount = initialExchange,
                            baseAmount = chosenAmountPerExchange
                        )
                    )
                } else it
            }
            .padding(8.dp)
    ) {
        Text(food.name, modifier = Modifier.weight(1f))
        Text(
            text = displayAmount,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
        )
        if (isDisabled) {
            Text(
                text = "(---)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Kullanıcının seçtiği bir besin maddesini temsil eder.
 * @property foodId Besinin kimliği.
 * @property name Besinin adı.
 * @property group Besin kategorisi (örnek: "Et", "Süt", vb.).
 * @property state Besinin durumu ("raw", "cooked", "neutral").
 * @property unit Ölçü birimi (örn: "gram").
 * @property amount Kaç değişim (exchange) seçildiği.
 * @property baseAmount Bir değişimin kaç gram/ml vb. değere karşılık geldiği.
 */
data class SelectedIngredient(
    val foodId: Int,
    val name: String,
    val group: String,
    var state: String,
    var unit: String,
    var amount: Double,
    var baseAmount: Double
)

/**
 * `FoodJournalEntry`, öğünlerle ilgili kullanıcı seçimlerini saklayan tabloyu temsil eder.
 *
 * @property entryId Veritabanı birincil anahtar.
 * @property mealId İlgili öğünün kimliği.
 * @property mealName İlgili öğünün adı.
 * @property dateTime Kayıt oluşturma/güncelleme zamanı.
 * @property ingredients Kullanıcının seçtiği besin malzemelerini içerir.
 * @property totalCalories Bu malzemelerle hesaplanan toplam kalori değeri.
 */
@Entity(tableName = "food_journal")
data class FoodJournalEntry(
    @PrimaryKey(autoGenerate = true) val entryId: Int = 0,
    val mealId: Int,
    val mealName: String,
    val dateTime: LocalDateTime,
    val ingredients: List<SelectedIngredient>,
    val totalCalories: Double
)
