package com.dagli.dietapp

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ExchangeActivity, besin değişim tablolarını doldurarak
 * kullanıcının günlük öğün planına temel oluşturacak değerleri girmesini sağlayan aktivitedir.
 */
class ExchangeActivity : ComponentActivity() {
    /**
     * @param savedInstanceState Önceden kaydedilen aktivite durum bilgilerini tutar.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ExchangeScreen(
                    onNextClick = { nutrientValues ->
                        // 1) Kullanıcı girdileri kaydedilir
                        saveNutrientValues(nutrientValues)

                        // 2) Toplam kalori hesaplanır ve saklanır
                        val totalCalories = CalorieCalculator.computeTotalCalories(nutrientValues).round2decimals()
                        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putString("planned_daily_calories", totalCalories.toString())
                            apply()
                        }

                        // 3) Sonraki ekrana geçiş yapılır
                        val intent = Intent(this, MealPlanningActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    /**
     * Kullanıcının girdiği besin değerlerini SharedPreferences içinde saklar.
     *
     * @param nutrientValues Kullanıcının girdiği besin kategorileri ve değerlerini içeren harita.
     */
    private fun saveNutrientValues(nutrientValues: Map<String, Double>) {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            nutrientValues.forEach { (key, value) ->
                putString(key, value.toString())
            }
            putBoolean("food_exchange_set", true)
            apply()
        }
    }
}

/**
 * ExchangeScreen, kullanıcının besin değişim tablolarını girdiği
 * ekrandan sorumlu olan Composable fonksiyonudur.
 *
 * @param onNextClick Kullanıcı "Devam" butonuna bastığında çağrılan callback.
 *                    Besin değerleri [Map] olarak geri döner.
 */
@Composable
fun ExchangeScreen(
    onNextClick: (Map<String, Double>) -> Unit
) {
    // Kullanıcının girmesi gereken besin kategorileri
    val nutrientCategories = listOf(
        "Süt/Yoğurt",
        "Et/Peynir/Yumurta",
        "Ekmek/Tahıl/Kurubaklagil",
        "Meyve",
        "Sebze",
        "Yağ",
        "Yağlı Tohumlar/Sert Kabuklu Kuruyemişler"
    )

    // Kullanıcıdan alınacak değerler (besin kategorisi -> metinsel değer) saklanır
    val nutrientValues = remember { mutableStateMapOf<String, String>().apply {
        nutrientCategories.forEach { cat -> put(cat, "0") }
    } }

    // Adım seçenekleri ve varsayılan adım
    val stepOptions = listOf(1.0, 0.5, 0.25, 0.10, 0.05)
    var currentStep by remember { mutableDoubleStateOf(1.0) }

    // Form validasyonu için bir hata bayrağı
    var isError by remember { mutableStateOf(false) }

    // Hesaplanan toplam kaloriyi dinamik olarak tutan değişken
    val totalCalories by remember {
        derivedStateOf {
            CalorieCalculator.computeTotalCalories(
                nutrientValues.mapValues { it.value.replace(',', '.').toDoubleOrNull() ?: 0.0 }
            )
        }
    }

    // Klavye ve odak yönetimi
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Ekran kaydırma durumu
    val scrollState = rememberScrollState()

    // Metin girişi tamamlandığında (IME action) gerçekleşecek davranış
    fun handleImeAction(index: Int) {
        if (index == nutrientCategories.lastIndex) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            focusManager.moveFocus(FocusDirection.Down)
        }
    }

    // Klavyenin görünür olup olmadığını takip eden state
    val keyboardVisible by keyboardAsState()

    Scaffold(
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    // Klavye çıktığında alt padding ayarlanır
                    .padding(bottom = if (keyboardVisible) 200.dp else 0.dp)
                    .padding(horizontal = 16.dp)
            ) {
                // Başlık
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Haydi Besin Değişim Tablonuzu Dolduralım",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Toplam Kalori: $totalCalories kcal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Adım Seçici Butonlar
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
                            Text(
                                text = formatNumber(step),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Her bir besin kategorisi için giriş alanı
                nutrientCategories.forEachIndexed { index, category ->
                    val userInput = nutrientValues[category] ?: "0"

                    NumberInputField(
                        label = category,
                        value = userInput,
                        onValueChange = { input ->
                            val sanitizedInput = input.replace(',', '.')
                            if (sanitizedInput.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                nutrientValues[category] = sanitizedInput
                            }
                        },
                        onIncrement = {
                            val currentVal = nutrientValues[category]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                            val newVal = (currentVal + currentStep).coerceAtMost(100.0) // Örnek limit
                            nutrientValues[category] = String.format("%.2f", newVal)
                        },
                        onDecrement = {
                            val currentVal = nutrientValues[category]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                            val newVal = (currentVal - currentStep).coerceAtLeast(0.0)
                            nutrientValues[category] = String.format("%.2f", newVal)
                        },
                        isError = isError && nutrientValues[category].isNullOrEmpty(),
                        onFocusLost = {
                            if (nutrientValues[category].isNullOrEmpty()) nutrientValues[category] = "0"
                        },
                        onImeAction = { handleImeAction(index) },
                        currentStep = currentStep,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                // Hata mesajı
                if (isError) {
                    Text(
                        text = "Lütfen bütün verileri doldur",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Devam butonu
                Button(
                    onClick = {
                        val incomplete = nutrientCategories.any { cat ->
                            nutrientValues[cat]?.isEmpty() == true
                        }
                        if (incomplete) {
                            isError = true
                        } else {
                            onNextClick(
                                nutrientValues.mapValues {
                                    it.value.replace(',', '.').toDouble()
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isError
                ) {
                    Text("Devam")
                }
            }
        }
    )
}

/**
 * NumberInputField, bir besin kategorisi için girilen
 * değeri yönetir ve artış/azalış butonları sunar.
 *
 * @param label Giriş alanının etiketi (besin kategorisi adı).
 * @param value Kullanıcının girdiği değer (metinsel).
 * @param onValueChange Değer her değiştiğinde çağrılan callback.
 * @param onIncrement Değerin arttırılması için callback.
 * @param onDecrement Değerin azaltılması için callback.
 * @param onFocusLost Kullanıcı odak (focus) dışına çıktığında çağrılan callback.
 * @param onImeAction IME (klavye) action tuşu tetiklendiğinde çağrılan callback.
 * @param modifier Görsel düzenleme için kullanılacak [Modifier].
 * @param isError Geçersiz girdi veya doğrulama hatası durumunu gösterir.
 * @param currentStep Kullanıcının artış/azalış için seçtiği adım boyutu.
 */
@Composable
fun NumberInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onIncrement: (Double) -> Unit,
    onDecrement: (Double) -> Unit,
    onFocusLost: () -> Unit,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    currentStep: Double
) {
    var isFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = if (value.toDoubleOrNull() == 0.0) "" else value
            )
        )
    }

    // value her değiştiğinde textFieldValue güncellenir
    LaunchedEffect(value) {
        textFieldValue = TextFieldValue(
            text = if (value.toDoubleOrNull() == 0.0) "" else value,
            selection = TextRange(value.length)
        )
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val sanitizedInput = newValue.text.replace(',', '.')
                val doubleValue = sanitizedInput.toDoubleOrNull()
                if (doubleValue != null) {
                    textFieldValue = TextFieldValue(
                        text = sanitizedInput,
                        selection = TextRange(sanitizedInput.length)
                    )
                    onValueChange(sanitizedInput)
                } else {
                    textFieldValue = newValue
                }
            },
            label = { Text(label) },
            isError = isError,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 48.dp)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (!focusState.isFocused) {
                        val sanitizedInput = textFieldValue.text.replace(',', '.')
                        val validatedValue = sanitizedInput.toDoubleOrNull()?.let {
                            validateDivisibleByStep(it, round = true)?.toString()
                        } ?: "0.00"
                        textFieldValue = TextFieldValue(
                            text = validatedValue,
                            selection = TextRange(validatedValue.length)
                        )
                        onValueChange(validatedValue)
                        onFocusLost()
                    }
                },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() }
            )
        )

        // Değer arttırma ve azaltma butonları
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        ) {
            IconButton(
                onClick = {
                    val currentVal = BigDecimal(value.ifBlank { "0" })
                    val step = BigDecimal(currentStep.toString())
                    val newValue = currentVal.add(step).setScale(2, RoundingMode.HALF_UP).toPlainString()
                    onValueChange(newValue)
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase"
                )
            }
            IconButton(
                onClick = {
                    val currentVal = BigDecimal(value.ifBlank { "0" })
                    val step = BigDecimal(currentStep.toString())
                    val newValue = currentVal.subtract(step)
                        .coerceAtLeast(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP)
                        .toPlainString()
                    onValueChange(newValue)
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease"
                )
            }
        }
    }
}

/**
 * Klavyenin görünür olup olmadığını takip eden Composable fonksiyon.
 *
 * @return [State] türünde bir [Boolean] döndürür. true ise klavye açıktır.
 */
@Composable
fun keyboardAsState(): State<Boolean> {
    val view = LocalView.current
    val keyboardVisible = remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            keyboardVisible.value = keypadHeight > screenHeight * 0.15 // Klavye yüksekliği eşiği
        }

        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    return keyboardVisible
}

/**
 * Belirli bir değerin, belirtilen [step] adımına bölünüp bölünemeyeceğini
 * kontrol eden ve gerekiyorsa yuvarlama yapan fonksiyon.
 *
 * @param value Kontrol edilecek çift (double) değer.
 * @param step Adım boyutu; varsayılan 0.05.
 * @param round true ise değeri en yakın adıma yuvarlar; false ise tam bölünemiyorsa null döner.
 *
 * @return Bölünebilir/yuvarlanmış değer veya [null] (bölünemiyorsa ve round=false ise).
 */
fun validateDivisibleByStep(value: Double, step: Double = 0.05, round: Boolean = false): Double? {
    val stepDecimal = BigDecimal(step.toString())
    val valueDecimal = BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP)

    return if (valueDecimal.remainder(stepDecimal).compareTo(BigDecimal.ZERO) == 0) {
        valueDecimal.toDouble()
    } else if (round) {
        valueDecimal.divide(stepDecimal, 0, RoundingMode.HALF_UP)
            .multiply(stepDecimal)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    } else {
        null
    }
}

/**
 * @param input Çift tipinde bir sayıyı metne çevirip, isteniyorsa
 * yuvarlamayı kontrol eder.
 * @return Formatlanmış ve gerekli görülürse yuvarlanmış metin değeri.
 */
fun formatNumber(input: Double): String {
    return String.format("%.2f", input)
}
