package com.dagli.dietapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dagli.dietapp.DistributionActivity.Companion.nutrientCategories
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DistributionEditActivity, kullanıcıların günlük besin değerlerini belirli öğünlere
 * dağıtmasına olanak tanıyan bir aktivitedir. Bu sınıf, dağıtım verilerini
 * SharedPreferences'tan yükler ve kaydeder, ayrıca gerektiğinde dağıtım ile ilgili veritabanı işlemlerini yönetir.
 */
class DistributionEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DietApp

        setContent {
            MaterialTheme {
                var dailyNutrients by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
                var meals by remember { mutableStateOf<List<Meal>>(emptyList()) }
                var distribution by remember { mutableStateOf<Map<Int, Map<String, Double>>>(emptyMap()) }
                var loading by remember { mutableStateOf(true) }

                LaunchedEffect(true) {
                    // SharedPreferences'tan günlük besin değerlerini yükle
                    val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    val loadedNutrients = nutrientCategories.associateWith { key ->
                        val value = sharedPref.getString(key, "0.0")
                        value?.toDoubleOrNull() ?: 0.0
                    }

                    // Veritabanından öğünleri yükle
                    val loadedMeals = withContext(Dispatchers.IO) {
                        app.db.mealDao().getAllMeals()
                    }

                    // SharedPreferences'tan mevcut dağılım (distribution) verilerini JSON olarak yükle
                    val loadedDistributionJson = sharedPref.getString("NutrientDistribution", "{}")

                    // Map<String, Map<String, Double>> türü için doğru TypeToken tanımla
                    val type = object : TypeToken<Map<String, Map<String, Double>>>() {}.type

                    // JSON'u Map<String, Map<String, Double>> olarak deserialize et
                    val loadedDistributionStringMap: Map<String, Map<String, Double>> = try {
                        Gson().fromJson(loadedDistributionJson, type) ?: emptyMap()
                    } catch (e: Exception) {
                        Log.e("DistributionEditActivity", "Error parsing distribution JSON", e)
                        emptyMap()
                    }

                    // Map<String, Map<String, Double>> yapısını Map<Int, Map<String, Double>> yapısına dönüştür
                    val loadedDistributionIntMap: Map<Int, Map<String, Double>> =
                        loadedDistributionStringMap.mapKeys { it.key.toIntOrNull() ?: -1 }
                            .filterKeys { it != -1 } // Int'e dönüşemeyenleri filtrele

                    dailyNutrients = loadedNutrients
                    meals = loadedMeals
                    distribution = loadedDistributionIntMap
                    loading = false
                }

                if (loading) {
                    LoadingScreen()
                } else {
                    // DistributionEditScreen'i composable olarak çiz
                    DistributionEditScreen(
                        dailyNutrients = dailyNutrients,
                        meals = meals,
                        nutrientCategories = nutrientCategories,
                        existingDistribution = distribution,
                        onSaveDistribution = { updatedDistribution ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                // Dağılım (distribution) haritasını JSON formatına dönüştür
                                val json = Gson().toJson(updatedDistribution)

                                // Verileri kaydet
                                val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                                sharedPref.edit().putString("NutrientDistribution", json).apply()
                                sharedPref.edit().putBoolean("nutrient_split_set", true).apply()


                                // Ana ekrana geri dön
                                withContext(Dispatchers.Main) {
                                    val intent = Intent(this@DistributionEditActivity, HomeActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * DistributionEditScreen composable'ı, kullanıcının günlük besin değerlerini
 * öğünlere dağıtmasına ve bunu kaydetmesine imkan tanır.
 *
 * @param dailyNutrients Günlük besin değerlerinin Map'i (örn. Protein, Karbonhidrat, Yağ)
 * @param meals Veritabanından gelen tüm öğünlerin listesi
 * @param nutrientCategories Besin kategorilerinin listesi (örn. ["protein", "carbs", "fat"])
 * @param existingDistribution Mevcut (kaydedilmiş) dağılım verileri
 * @param onSaveDistribution Kullanıcı "Kaydet" butonuna bastığında tetiklenecek callback fonksiyonu
 */
@Composable
fun DistributionEditScreen(
    dailyNutrients: Map<String, Double>,
    meals: List<Meal>,
    nutrientCategories: List<String>,
    existingDistribution: Map<Int, Map<String, Double>>,
    onSaveDistribution: (Map<Int, Map<String, Double>>) -> Unit
) {
    // meal.id kullanarak doğrudan bir map oluşturuyor
    val indexedMeals = remember(meals) {
        meals.map { meal -> meal.id to meal }
    }

    // distribution, mevcut veya varsayılan değerlere göre başlatılır
    val distribution = remember {
        val dist = mutableStateMapOf<Int, SnapshotStateMap<String, Double>>()
        indexedMeals.forEach { (id, _) ->
            val existingMealDistribution = existingDistribution[id] ?: emptyMap()
            val nutrientMap = mutableStateMapOf<String, Double>()
            nutrientCategories.forEach { nutrient ->
                nutrientMap[nutrient] = existingMealDistribution[nutrient] ?: 0.0
            }
            dist[id] = nutrientMap
        }
        dist
    }

    /**
     * Bir besin kategorisinin (örn. protein) toplamda kaç birim kullanıldığını hesaplar.
     *
     * @param nutrient Hesaplanacak besin kategorisi
     * @return Toplam kullanılan miktar
     */
    fun calculateTotalUsed(nutrient: String): Double {
        return distribution.values.sumOf { it[nutrient] ?: 0.0 }
    }

    /**
     * Bir besin kategorisinde (örn. protein) geriye kaç birim kaldığını hesaplar.
     *
     * @param nutrient Hesaplanacak besin kategorisi
     * @return Kalan miktar
     */
    fun remaining(nutrient: String): Double {
        val max = dailyNutrients[nutrient] ?: 0.0
        val used = calculateTotalUsed(nutrient)
        return max - used
    }

    var showDetails by remember { mutableStateOf(true) }

    // Adım seçimi için buton mantığı
    val stepOptions = listOf(1.0, 0.5, 0.25, 0.10, 0.05)
    var currentStep by remember { mutableStateOf(0.25) } // Varsayılan adım (0.25)

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    /**
     * Hata gösterimi için kullanılır.
     *
     * @param message Kullanıcıya gösterilecek mesaj
     */
    suspend fun showError(message: String) {
        snackbarHostState.showSnackbar(message)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(innerPadding)
        ) {
            // Başlık
            Text(
                text = "Dağıtımlarınızı Düzenleyin",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            // Adım seçici butonlar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Adım:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 8.dp)
                )
                stepOptions.forEach { step ->
                    Button(
                        onClick = { currentStep = step },
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentStep == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (currentStep == step) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(36.dp)
                    ) {
                        Text(text = String.format("%.2f", step), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Detayları göster/gizle seçeneği
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Özeti Göster",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = showDetails,
                    onCheckedChange = { showDetails = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }

            // Besin özetini göster
            if (showDetails) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Değişim Özetim",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        nutrientCategories.forEach { nutrient ->
                            val max = dailyNutrients[nutrient] ?: 0.0
                            val rem = remaining(nutrient).round2decimals()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = nutrient,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Max: $max, Kalan: $rem",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Besin dağılımına sahip öğün listesi
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(indexedMeals, key = { it.first }) { (id, meal) ->
                    // Bu öğünün toplam kalorisini hesapla
                    val mealDistribution = distribution[id] ?: emptyMap()
                    val totalCalories = CalorieCalculator.computeTotalCaloriesForMeal(mealDistribution)

                    MealNutrientAllocation(
                        meal = meal,
                        nutrients = nutrientCategories,
                        getValue = { nutrient -> distribution[id]?.get(nutrient) ?: 0.0 },
                        onIncrement = { nutrient ->
                            val oldVal = distribution[id]?.get(nutrient) ?: 0.0
                            val rem = remaining(nutrient)
                            // Adım eklemeden sonra değer yuvarlanır
                            val newVal = (oldVal + currentStep).round2decimals()
                            // Küçük bir epsilon ekleyerek kalan değeri aşmamasını sağlıyoruz
                            if (newVal <= (oldVal + rem + 0.000001)) {
                                distribution[id]?.set(nutrient, newVal)
                            } else {
                                coroutineScope.launch {
                                    showError("$nutrient için daha fazla paylaşım yapamazsınız.")
                                }
                            }
                        },
                        onDecrement = { nutrient ->
                            val oldVal = distribution[id]?.get(nutrient) ?: 0.0
                            if (oldVal >= currentStep) {
                                val newVal = (oldVal - currentStep).round2decimals()
                                distribution[id]?.set(nutrient, newVal)
                            }
                        },
                        totalCalories = totalCalories
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Kaydet butonu
            ElevatedButton(
                onClick = {
                    val anyRemaining = nutrientCategories.any { remaining(it) != 0.0 }
                    if (anyRemaining) {
                        coroutineScope.launch {
                            showError("Lütfen bütün değişimlerinizi paylaştırın.")
                        }
                    } else {
                        val finalDist = distribution.mapValues { entry -> entry.value.toMap() }
                        onSaveDistribution(finalDist)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "Kaydet & Ana Ekrana Dön",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/**
 * MealNutrientAllocation composable'ı, tek bir öğünün besin dağılımını yönetir.
 *
 * @param meal İlgili öğün bilgileri (örneğin Ana Öğün ya da Ara Öğün)
 * @param nutrients Besin kategorileri listesi (örn. ["protein", "carbs", "fat"])
 * @param getValue Her bir besin kategorisi için o anki değeri almak üzere callback fonksiyonu
 * @param onIncrement İlgili besin kategorisi değerini artırmak için çağrılan callback fonksiyonu
 * @param onDecrement İlgili besin kategorisi değerini azaltmak için çağrılan callback fonksiyonu
 * @param totalCalories Bu öğüne ait besin dağılımlarının hesaplanan toplam kalori değeri
 */
@Composable
fun MealNutrientAllocation(
    meal: Meal,
    nutrients: List<String>,
    getValue: (String) -> Double,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    totalCalories: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Öğün başlığı
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (meal.type == MealType.MAIN_MEAL) "Ana Öğün: ${meal.name}" else "Ara Öğün: ${meal.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$totalCalories kcal",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Besin değerleri dağılım satırları
            nutrients.forEach { nutrient ->
                NutrientAllocationRow(
                    nutrient = nutrient,
                    value = getValue(nutrient),
                    onIncrement = { onIncrement(nutrient) },
                    onDecrement = { onDecrement(nutrient) },
                )
            }
        }
    }
}
