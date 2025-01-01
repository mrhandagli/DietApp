package com.dagli.dietapp

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * MealPlanningActivity, uygulamadaki öğünlerin (ana öğün ve ara öğün) planlanmasını yöneten ekrandır.
 * Kullanıcı burada öğünlerin başlama/bitiş saatlerini ve isimlerini ayarlar.
 */
class MealPlanningActivity : ComponentActivity() {
    /**
     * @param savedInstanceState Önceden kaydedilen aktivite durum bilgilerini tutar.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DietApp

        setContent {
            MaterialTheme {
                MealPlanningScreen(
                    onNextClick = { mealUIList ->
                        // Ana öğün ve ara öğün ayrımı yapılır
                        val mainMealUIs = mealUIList.filter { it.parentIndex == null }
                        val snackUIs = mealUIList.filter { it.parentIndex != null }

                        // Arka planda veritabanı işlemleri
                        lifecycleScope.launch(Dispatchers.IO) {
                            // Tüm öğünler temizlenir
                            app.db.mealDao().clearAll()

                            // Ana öğünler veritabanına eklenir
                            val mainMeals = mainMealUIs.map {
                                Meal(
                                    name = it.name,
                                    type = MealType.MAIN_MEAL,
                                    startTime = it.startTime,
                                    endTime = it.endTime,
                                    parentMealId = null
                                )
                            }
                            // Eklenen ana öğünlerin ID'leri geri döner
                            val mainMealIds = app.db.mealDao().insertMeals(mainMeals)

                            // Ara öğünler eklenir (ana öğün ID'sine bağlanır)
                            val snacks = snackUIs.map { snackUI ->
                                val mainMealIndex = snackUI.parentIndex!!
                                val parentMealRowId = mainMealIds[mainMealIndex]
                                Meal(
                                    name = snackUI.name,
                                    type = MealType.SNACK,
                                    startTime = snackUI.startTime,
                                    endTime = snackUI.endTime,
                                    parentMealId = parentMealRowId.toInt()
                                )
                            }
                            if (snacks.isNotEmpty()) {
                                app.db.mealDao().insertMeals(snacks)
                            }

                            // İşlem bittikten sonra UI'da işlem yapılır
                            withContext(Dispatchers.Main) {
                                val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                                // Yemek planının ayarlandığını işaretler
                                sharedPref.edit().putBoolean("meal_plan_set", true).apply()
                                // Bir sonraki aktiviteye geçiş
                                val intent = Intent(this@MealPlanningActivity, DistributionActivity::class.java)
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

/**
 * MealUI, kullanıcı arayüzünde görüntülenen öğünü temsil eden veri sınıfıdır.
 *
 * @param id Veritabanına kaydedilmişse öğünün kimliğini tutar (nullable).
 * @param name Öğünün adı (ör. "Kahvaltı", "Ara Öğün" vb.).
 * @param type [MealType] belirterek öğünün ana öğün mü, ara öğün mü olduğunu gösterir.
 * @param startTime Öğünün başlama saati.
 * @param endTime Öğünün bitiş saati.
 * @param parentIndex Eğer ara öğünse, hangi ana öğüne bağlı olduğunu gösterir (indis).
 */
data class MealUI(
    val id: Int?,
    val name: String,
    val type: MealType,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val parentIndex: Int?
)

/**
 * MealPlanningScreen, kullanıcının ana ve ara öğünlerini ekleyip düzenlediği Composable ekrandır.
 *
 * @param onNextClick Kullanıcı "Devam Et" butonuna bastığında çağrılan callback.
 *                    Parametre olarak, [MealUI] listesini geri döndürür.
 * @param initialMeals Eğer öğünler önceden tanımlanmışsa, bu liste ekranda doldurulur (varsayılan boş).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanningScreen(
    onNextClick: (List<MealUI>) -> Unit,
    initialMeals: List<MealUI> = emptyList()
) {
    // Tüm öğünlerin tutulduğu liste
    val allMeals = remember { mutableStateListOf<MealUI>() }

    // Ana öğün sayısını takip eder (ör. "Kahvaltı", "Öğle Yemeği" vb.)
    var mainMealCount by remember { mutableStateOf(0) }

    // initialMeals doluysa, ekrana ilk yüklendiğinde listeye eklenir
    LaunchedEffect(initialMeals) {
        if (initialMeals.isNotEmpty()) {
            allMeals.addAll(initialMeals)
            mainMealCount = initialMeals.count { it.parentIndex == null }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Öğün Planlama") }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .padding(innerPadding)
        ) {
            Text(
                text = "Ana ve ara öğünlerinizi belirleyelim.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            // Ana Öğün Ekle butonu
            ElevatedButton(
                onClick = {
                    val mainMealIndex = mainMealCount
                    allMeals.add(
                        MealUI(
                            name = "Kahvaltı",
                            type = MealType.MAIN_MEAL,
                            startTime = LocalTime.of(8, 0),
                            endTime = LocalTime.of(9, 0),
                            parentIndex = null,
                            id = null,
                        )
                    )
                    mainMealCount += 1
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Ana Öğün Ekle")
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Ana Öğün Ekle")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Kullanıcının eklediği tüm öğünler listelenir
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(allMeals) { index, mealUI ->
                    MealItem(
                        mealUI = mealUI,
                        index = index,
                        allMeals = allMeals,
                        mainMealCount = mainMealCount,
                        onDelete = {
                            // Öğünü listeden kaldır
                            allMeals.removeAt(index)
                            // Ana öğünse, sayaç azaltılır ve ilgili ara öğünler de silinir
                            if (mealUI.type == MealType.MAIN_MEAL) {
                                mainMealCount -= 1
                                allMeals.removeAll { it.parentIndex == index }
                            }
                        },
                        onAddSnack = { mealIndex ->
                            val mainMealIndex = computeMainMealIndex(allMeals, mealIndex)
                            if (mainMealIndex != -1) {
                                val parentMeal = allMeals[mainMealIndex]
                                allMeals.add(
                                    MealUI(
                                        name = "Ara Öğün",
                                        type = MealType.SNACK,
                                        startTime = parentMeal.endTime.plusHours(1),
                                        endTime = parentMeal.endTime.plusHours(2),
                                        parentIndex = mainMealIndex,
                                        id = null
                                    )
                                )
                            }
                        },
                        onMealChange = { updatedMeal ->
                            // Mevcut indekste öğünü günceller
                            allMeals[index] = updatedMeal
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // "Devam Et" butonu
            ElevatedButton(
                onClick = { onNextClick(allMeals.toList()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(text = "Devam Et")
            }
        }
    }
}

/**
 * computeMainMealIndex, belirli bir [mealIndex]'e sahip öğünün hangi ana öğüne ait olduğunu hesaplar.
 *
 * @param allMeals Tüm öğünlerin listesi.
 * @param mealIndex Üzerinde işlem yapılan öğünün indeksi.
 * @return İlgili öğün ana öğün listesi içindeki sırası. Bulunamazsa -1 döner.
 */
fun computeMainMealIndex(allMeals: List<MealUI>, mealIndex: Int): Int {
    // parentIndex'i null olanlar ana öğün olarak kabul edilir
    val mainMeals = allMeals.filter { it.parentIndex == null }
    val targetMainMeal = allMeals[mealIndex]
    val mainMealIndex = mainMeals.indexOf(targetMainMeal)
    return mainMealIndex
}

/**
 * MealItem, tek bir öğünün UI gösterimini ve etkileşimlerini sağlar.
 *
 * @param mealUI Gösterilecek öğünün verilerini içerir.
 * @param index Öğünün listedeki konumu.
 * @param allMeals Tüm öğünlerin listesi.
 * @param mainMealCount Ana öğün sayısı.
 * @param onDelete Öğünü silme işlemi için callback.
 * @param onAddSnack Ara öğün ekleme işlemi için callback.
 * @param onMealChange Öğünde yapılan değişiklikleri üst seviyeye bildiren callback.
 */

@Composable
fun MealItem(
    mealUI: MealUI,
    index: Int,
    allMeals: List<MealUI>,
    mainMealCount: Int,
    onDelete: () -> Unit,
    onAddSnack: (Int) -> Unit,
    onMealChange: (MealUI) -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Ana öğün veya ara öğün başlığı
                Text(
                    text = if (mealUI.type == MealType.MAIN_MEAL) "Ana Öğün" else "Ara Öğün",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Öğünü Kaldır")
                }
            }

            // Öğün adı giriş alanı
            MealNameInput(
                mealName = mealUI.name,
                onNameChange = { newName -> onMealChange(mealUI.copy(name = newName)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Başlangıç saati
            TimePicker(
                label = "Başlangıç",
                time = mealUI.startTime,
                onTimeChange = { newStartTime ->
                    onMealChange(mealUI.copy(startTime = newStartTime))
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Bitiş saati
            TimePicker(
                label = "Bitiş",
                time = mealUI.endTime,
                onTimeChange = { newEndTime ->
                    onMealChange(mealUI.copy(endTime = newEndTime))
                }
            )

            // Ara öğün ekleme butonu (sadece ana öğünde)
            if (mealUI.type == MealType.MAIN_MEAL) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onAddSnack(index) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Ara Öğün Ekle")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Bu Öğünden Sonra Ara Öğün Ekle")
                }
            }
        }
    }
}

/**
 * MealNameInput, öğün adını seçmek veya özel bir öğün adı girmek için kullanılan alanı yönetir.
 *
 * @param mealName Şu anki öğün adı.
 * @param onNameChange Yeni öğün adı girildiğinde çağrılan callback.
 */
@Composable
fun MealNameInput(mealName: String, onNameChange: (String) -> Unit) {
    var showDropdown by remember { mutableStateOf(false) }
    var isCustomName by remember { mutableStateOf(false) }

    // Varsayılan öğün adları
    val defaultMealNames = listOf("Kahvaltı", "Öğle", "Akşam", "Kuşluk", "İkindi", "Gece", "Özel")

    Column {
        // Öğün adını seçmek için tıklanabilen OutlinedTextField
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDropdown = true }
        ) {
            OutlinedTextField(
                value = if (mealName.isEmpty()) "Öğün Adını Belirle" else mealName,
                onValueChange = {},
                label = { Text("Öğün Adı") },
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Öğün Adını Seç")
                }
            )
        }
        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false }
        ) {
            defaultMealNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        showDropdown = false
                        if (name == "Özel") {
                            isCustomName = true
                            onNameChange("")
                        } else {
                            isCustomName = false
                            onNameChange(name)
                        }
                    }
                )
            }
        }
        // Eğer "Özel" seçilmişse, kullanıcı manuel olarak metin girebilir
        if (isCustomName) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = mealName,
                onValueChange = onNameChange,
                label = { Text("Özel Öğün Adını Gir") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
            )
        }
    }
}

/**
 * TimePicker, kullanıcıya TimePickerDialog göstererek bir zaman seçmesini sağlar.
 *
 * @param label Zamanı gösterecek text alanının etiketi.
 * @param time Kullanıcının seçtiği veya varsayılan saat.
 * @param onTimeChange Yeni saat/minute seçildiğinde çağrılan callback.
 */
@Composable
fun TimePicker(label: String, time: LocalTime, onTimeChange: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val timePickerDialog = TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        onTimeChange(LocalTime.of(hourOfDay, minute))
                    },
                    time.hour,
                    time.minute,
                    true
                )
                timePickerDialog.show()
            }
    ) {
        OutlinedTextField(
            value = time.format(timeFormatter),
            onValueChange = {},
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            enabled = false,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "Zamanı Belirle"
                )
            }
        )
    }
}

/**
 * Meal, veritabanında saklanacak öğün bilgilerini temsil eden veri sınıfıdır.
 *
 * @param id Öğünün birincil anahtar (otomatik üretilir).
 * @param name Öğün adı (ör. "Kahvaltı").
 * @param type [MealType] ile öğünün ana veya ara öğün olduğunu belirtir.
 * @param startTime Öğünün başlangıç saati.
 * @param endTime Öğünün bitiş saati.
 * @param parentMealId Eğer ara öğünse, hangi ana öğüne bağlı olduğunu gösterir.
 */
@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "meal_type")
    val type: MealType,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val parentMealId: Int? = null
)

/**
 * MealType, öğünün türünü ifade eder:
 * - [MAIN_MEAL] ana öğünler için
 * - [SNACK] ara öğünler için
 */
enum class MealType {
    MAIN_MEAL,
    SNACK
}
