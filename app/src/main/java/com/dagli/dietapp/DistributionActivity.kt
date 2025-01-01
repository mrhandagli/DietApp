package com.dagli.dietapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.dagli.dietapp.ui.theme.DietAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * DistributionActivity, kullanıcının günlük besin değişim değerlerini
 * öğünlere dağıtmasını sağlayan ekrandan sorumlu olan aktivitedir.
 */
class DistributionActivity : ComponentActivity() {
    /**
     * @param savedInstanceState Daha önce kaydedilen durum bilgilerini içerir (örneğin ekran döndürme).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DietApp

        setContent {
            DietAppTheme {
                var dailyNutrients by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
                var meals by remember { mutableStateOf<List<Meal>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }

                // Aktivite ilk açıldığında veriler yüklenir
                LaunchedEffect(true) {
                    val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    // Her bir besin kategorisinin günlük değerini SharedPreferences'tan çeker
                    val loadedNutrients = nutrientCategories.associateWith { category ->
                        val value = sharedPref.getString(category, "0")
                        value?.toDoubleOrNull() ?: 0.0
                    }

                    // Veritabanından öğünleri çeker
                    val loadedMeals = withContext(Dispatchers.IO) {
                        app.db.mealDao().getAllMeals()
                    }

                    dailyNutrients = loadedNutrients
                    meals = loadedMeals
                    loading = false
                }

                if (loading) {
                    LoadingScreen()
                } else {
                    DistributionScreen(
                        dailyNutrients = dailyNutrients,
                        meals = meals,
                        nutrientCategories = nutrientCategories,
                        onSaveDistribution = { distribution ->
                            // Kullanıcının dağıtımını SharedPreferences içine kaydet
                            val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                            val json = Gson().toJson(distribution)
                            sharedPref.edit().putString("NutrientDistribution", json).apply()
                            sharedPref.edit().putBoolean("nutrient_split_set", true).apply()

                            val intent = Intent(this@DistributionActivity, HomeActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        // Uygulamadaki besin kategorilerinin listesi
        val nutrientCategories = listOf(
            "Süt/Yoğurt",
            "Et/Peynir/Yumurta",
            "Ekmek/Tahıl/Kurubaklagil",
            "Meyve",
            "Sebze",
            "Yağ",
            "Yağlı Tohumlar/Sert Kabuklu Kuruyemişler"
        )
    }
}

/**
 * Basit bir yüklenme göstergesi (Progress Indicator) için kullanılır.
 */
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * DistributionScreen, kullanıcıya günlük besin değerlerini öğünlere dağıtma
 * arayüzü sunar.
 *
 * @param dailyNutrients Kullanıcının günlük ataması gereken besin değerleri (kategori -> miktar).
 * @param meals Veritabanından çekilen öğün listesi.
 * @param nutrientCategories Besin kategorilerinin listesi.
 * @param onSaveDistribution Kullanıcı dağıtımı tamamladığında çağrılan callback.
 *                           Parametresi Map<Int, Map<String, Double>>: öğün ID -> (kategori -> miktar).
 */
@Composable
fun DistributionScreen(
    dailyNutrients: Map<String, Double>,
    meals: List<Meal>,
    nutrientCategories: List<String>,
    onSaveDistribution: (Map<Int, Map<String, Double>>) -> Unit
) {
    // Her öğüne bir ID vererek eşleştirme yapar
    val indexedMeals = remember {
        var globalId = 1
        meals.map { globalId++ to it }
    }

    // Kullanıcının öğün bazında kategori dağılımını tutar
    val distribution = remember {
        mutableStateMapOf<Int, SnapshotStateMap<String, Double>>().apply {
            indexedMeals.forEach { (id, _) ->
                val nutrientMap = mutableStateMapOf<String, Double>()
                nutrientCategories.forEach { nutrient ->
                    nutrientMap[nutrient] = 0.0
                }
                this[id] = nutrientMap
            }
        }
    }

    // Adım seçenekleri ve varsayılan adım
    val stepOptions = listOf(1.0, 0.5, 0.25, 0.10, 0.05)
    var currentStep by remember { mutableStateOf(1.0) }

    /**
     * Belirli bir besin kategorisi için kullanıcı tarafından toplamda ne kadar kullanıldığını hesaplar.
     * @param nutrient Besin kategorisi adı.
     * @return Kullanıcının tüm öğünler için girdiği toplam miktar.
     */
    fun calculateTotalUsed(nutrient: String): Double {
        return distribution.values.sumOf { it[nutrient] ?: 0.0 }
    }

    /**
     * Bir besin kategorisinde geriye kaç değişim kaldığını hesaplar.
     * @param nutrient Besin kategorisi adı.
     * @return Kalan değişim miktarı (double).
     */
    fun remaining(nutrient: String): Double {
        val max = dailyNutrients[nutrient] ?: 0.0
        val used = calculateTotalUsed(nutrient)
        return max - used
    }

    // Kullanıcıya kalan değerleri gösteren özet menü kontrolü
    var showSummary by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    /**
     * Hata mesajlarını göstermek için ortak bir fonksiyon
     * @param message Gösterilecek hata metni
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
            // Ekran başlığı
            Text(
                text = "Besinleri Öğünlere Paylaştırın",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            // Adım Seçici Butonlar
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
                        Text(
                            text = String.format("%.2f", step),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Kalan Değerleri Göster/Gizle seçeneği
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Kalan Değişim Değerlerini Göster",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = showSummary,
                    onCheckedChange = { showSummary = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }

            // Eğer kullanıcı kalan değerleri görmek istiyorsa gösterilecek kart
            if (showSummary) {
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
                            text = "Kalan Değişim Değerleri",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        nutrientCategories.forEach { nutrient ->
                            val max = dailyNutrients[nutrient] ?: 0.0
                            val rem = remaining(nutrient)
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
                                    text = "Max: %.2f, Kalan: %.2f".format(max, rem),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Öğünlerin gösterildiği liste ve dağıtım
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(indexedMeals, key = { _, item -> item.first }) { _, (id, meal) ->

                    val mealDistribution = distribution[id]?.toMap() ?: emptyMap()
                    val totalCalories = CalorieCalculator.computeTotalCaloriesForMeal(mealDistribution)

                    MealNutrientAllocation(
                        meal = meal,
                        nutrients = nutrientCategories,
                        getValue = { nutrient -> distribution[id]?.get(nutrient) ?: 0.0 },
                        onIncrement = { nutrient ->
                            val oldVal = distribution[id]?.get(nutrient) ?: 0.0
                            val rem = remaining(nutrient)
                            val newVal = (oldVal + currentStep).coerceAtMost(oldVal + rem).round2decimals()
                            distribution[id]?.set(nutrient, newVal)
                        },
                        onDecrement = { nutrient ->
                            val oldVal = distribution[id]?.get(nutrient) ?: 0.0
                            val newVal = (oldVal - currentStep).coerceAtLeast(0.0).round2decimals()
                            distribution[id]?.set(nutrient, newVal)
                        },

                        totalCalories = totalCalories
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Kaydetme butonu
            ElevatedButton(
                onClick = {
                    val anyRemaining = nutrientCategories.any { remaining(it) > 0.0 }
                    if (anyRemaining) {
                        // Eğer hala dağıtılmamış besin varsa, uyarı göster
                        coroutineScope.launch {
                            showError("Lütfen bütün besinlerinizi paylaştırın.")
                        }
                    } else {
                        // Dağıtım tamamlandı; veriler callback ile üst seviyeye iletilir
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
                    text = "Kaydet ve Kurulumu Bitir",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/**
 * Küçük bir uzantı fonksiyonu, double değerleri 2 ondalık haneye yuvarlar.
 *
 * @receiver Double değeri.
 * @return 2 ondalık basamağa yuvarlanmış yeni Double.
 */
fun Double.round2decimals(): Double {
    return (this * 100.0).roundToInt() / 100.0
}

/**
 * MealNutrientAllocation, tek bir öğün için her bir besin kategorisine ait
 * dağıtım verisini (arttırma/azaltma) yönetmekten sorumludur.
 *
 * @param meal Gösterilecek öğün ([Meal] nesnesi).
 * @param nutrients Besin kategorileri listesi.
 * @param getValue Belirli bir kategori için o öğüne ait mevcut değerini döndürür.
 * @param onIncrement Kullanıcı değeri arttırmak istediğinde çağrılan callback.
 * @param onDecrement Kullanıcı değeri azaltmak istediğinde çağrılan callback.
 * @param totalCalories Bu öğüne ait dağıtılan besin kategorilerinin hesaplanan toplam kalori değeri.
 */

/**
 * NutrientAllocationRow, bir besin kategorisine ait +/– butonları ve mevcut değeri gösterir.
 *
 * @param nutrient Besin kategorisi adı.
 * @param value Şu anki değer.
 * @param onIncrement Değeri arttırma callback.
 * @param onDecrement Değeri azaltma callback.
 */
@Composable
fun NutrientAllocationRow(
    nutrient: String,
    value: Double,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {


    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = nutrient,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Değer azaltma butonu
            IconButton(
                onClick = { onDecrement() },
                modifier = Modifier
                    .size(32.dp),
                enabled = value >= 0.05
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = if (value >= 0.05)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Değer gösterme
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Değer arttırma butonu
            IconButton(
                onClick = { onIncrement() },
                modifier = Modifier
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
