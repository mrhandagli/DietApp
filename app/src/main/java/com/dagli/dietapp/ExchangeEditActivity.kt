package com.dagli.dietapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Besin değişim değerlerini düzenlemek için kullanılan aktivite.
 */
class ExchangeEditActivity : ComponentActivity() {

    /**
     * Aktivite oluşturulurken çağrılan fonksiyon.
     *
     * @param savedInstanceState Önceden kaydedilmiş durum verileri, eğer mevcutsa.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ExchangeEditScreen(
                    onSaveClick = { updatedValues ->
                        lifecycleScope.launch {
                            saveNutrientValues(updatedValues)
                            resetDistributionData()
                            navigateToDistributionEdit()
                        }
                    }
                )
            }
        }
    }

    /**
     * Besin değerlerini (nutrientValues) SharedPreferences'e kaydeder.
     *
     * @param nutrientValues Kaydedilecek besin değerleri (ör. "Süt/Yoğurt" -> 2.0).
     */
    private suspend fun saveNutrientValues(nutrientValues: Map<String, Double>) {
        withContext(Dispatchers.IO) {
            val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            with(sharedPref.edit()) {
                nutrientValues.forEach { (key, value) ->
                    putString(key, value.toString()) // Hassasiyeti korumak için string olarak kaydediliyor
                }
                apply()
            }
        }
    }

    /**
     * Daha önce kaydedilmiş besin dağılımı verilerini temizler (SharedPreferences'ten siler).
     */
    private suspend fun resetDistributionData() {
        withContext(Dispatchers.IO) {
            val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            with(sharedPref.edit()) {
                remove("NutrientDistribution")
                putBoolean("nutrient_split_set", false)
                apply()
            }
        }
        Log.d("ExchangeEditActivity", "Besin dağılım verileri sıfırlandı.")
    }

    /**
     * DistributionEditActivity'ye geçiş yapmak için kullanılır.
     */
    private fun navigateToDistributionEdit() {
        val intent = Intent(this, DistributionEditActivity::class.java)
        startActivity(intent)
        finish()
    }
}

/**
 * Değişim değerlerini güncellemek için kullanılan Composable ekran.
 *
 * @param onSaveClick Kaydet butonuna basıldığında çağrılan lambda. Güncellenmiş besin değerlerini parametre olarak alır.
 */
@Composable
fun ExchangeEditScreen(
    onSaveClick: (Map<String, Double>) -> Unit
) {
    // Besin kategorileri listesi
    val nutrientCategories = listOf(
        "Süt/Yoğurt",
        "Et/Peynir/Yumurta",
        "Ekmek/Tahıl/Kurubaklagil",
        "Meyve",
        "Sebze",
        "Yağ",
        "Yağlı Tohumlar/Sert Kabuklu Kuruyemişler"
    )

    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

    // Kaydedilmiş veya varsayılan 0 değerlerini tutmak için Map
    val nutrientValues = remember {
        mutableStateMapOf<String, String>().apply {
            nutrientCategories.forEach { category ->
                val savedValue = sharedPref.getString(category, "0") ?: "0"
                this[category] = savedValue
            }
        }
    }

    // Artış/azalış seçenekleri
    val stepOptions = listOf(1.0, 0.5, 0.25, 0.1, 0.05)
    var currentStep by remember { mutableStateOf(1.0) }
    var isError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    /**
     * İmeAction handling fonksiyonu.
     *
     * @param index Bulunulan besin kategorisinin indeks değeri.
     */
    fun handleImeAction(index: Int) {
        if (index == nutrientCategories.lastIndex) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            focusManager.moveFocus(FocusDirection.Down)
        }
    }


    // Toplam kaloriyi hesapla
    val totalCalories by remember {
        derivedStateOf {
            CalorieCalculator.computeTotalCalories(
                nutrientValues.mapValues { it.value.replace(',', '.').toDoubleOrNull() ?: 0.0 }
            )
        }
    }

    Scaffold(
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                // Başlık (Header)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Besin Değişim Değerlerini Güncelle",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // Toplam Kalori Bilgisi
                    Text(
                        text = "Toplam Kalori: $totalCalories kcal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    // Adım Seçici (Step Selector)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Adım:",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(1.dp)
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
                                    .padding(horizontal = 1.dp)
                                    .height(36.dp)
                            ) {
                                Text(text = formatNumber(step), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Girdiler
                    nutrientCategories.forEachIndexed { index, category ->
                        NumberInputField(
                            label = category,
                            value = nutrientValues[category] ?: "0",
                            onValueChange = { input ->
                                // Kullanıcı virgül kullanırsa nokta ile değiştir
                                val sanitizedInput = input.replace(',', '.')
                                // Sadece ondalık formata uygun değerleri kabul et
                                if (sanitizedInput.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                    nutrientValues[category] = sanitizedInput
                                }
                            },
                            onIncrement = { updatedValue ->
                                // Mevcut değeri step değeri kadar artır
                                val currentVal = nutrientValues[category]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                                val newVal = (currentVal + currentStep).coerceAtMost(100.0) // Örnek limit
                                nutrientValues[category] = String.format("%.2f", newVal)
                            },
                            onDecrement = { updatedValue ->
                                // Mevcut değeri step değeri kadar azalt
                                val currentVal = nutrientValues[category]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                                val newVal = (currentVal - currentStep).coerceAtLeast(0.0)
                                nutrientValues[category] = String.format("%.2f", newVal)
                            },
                            isError = isError && nutrientValues[category].isNullOrEmpty(),
                            onFocusLost = {
                                // Kullanıcı alanı boş bıraktıysa otomatik olarak "0" yap
                                if (nutrientValues[category].isNullOrEmpty()) nutrientValues[category] = "0"
                            },
                            onImeAction = { handleImeAction(index) },
                            currentStep = currentStep, // Şu anki adım değeri
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }

                    // Hata mesajı
                    if (isError) {
                        Text(
                            text = "Lütfen tüm alanları doldurun.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Kaydet Butonu
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            // Kullanıcı herhangi bir kategoride hatalı/uyuşmayan bir değer girmiş mi kontrol et
                            val hasInvalidValues = nutrientCategories.any { category ->
                                val value = nutrientValues[category]?.replace(',', '.')?.toDoubleOrNull()
                                value == null || validateDivisibleByStep(value, round = false) == null
                            }

                            if (hasInvalidValues) {
                                isError = true
                            } else {
                                isError = false
                                val updatedValues = nutrientValues.mapValues { (_, value) ->
                                    val doubleValue = value.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    // Adıma tam bölünemiyorsa yuvarla
                                    validateDivisibleByStep(doubleValue, round = true) ?: doubleValue
                                }
                                onSaveClick(updatedValues)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isError
                    ) {
                        Text("Kaydet ve Devam Et")
                    }
                }
            }
        }
    )
}
