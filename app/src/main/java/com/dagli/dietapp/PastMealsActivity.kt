package com.dagli.dietapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/**
 * `PastMealsActivity`, kullanıcının geçmiş öğünlerini listeleyip
 * görüntülemesine olanak tanıyan bir aktivitedir.
 */
class PastMealsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as DietApp
        val journalDao = app.db.foodJournalDao()
        val mealDao = app.db.mealDao()

        setContent {
            MaterialTheme {
                var loading by remember { mutableStateOf(true) }
                var entries by remember { mutableStateOf<List<FoodJournalEntry>>(emptyList()) }
                var meals by remember { mutableStateOf<Map<Int, Meal>>(emptyMap()) }

                /**
                 * Uygulama açıldığında veya yeniden çizilirken (recomposition) çalışarak
                 * geçmiş öğün kayıtlarını (FoodJournalEntry) ve ilgili Meal nesnelerini
                 * veritabanından yükler.
                 */
                LaunchedEffect(true) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val allEntries = journalDao.getAllEntries()

                        // Bu girişler içindeki benzersiz mealId değerlerini al
                        val mealIds = allEntries.map { it.mealId }.distinct()

                        // Her mealId için veritabanından Meal nesnesini getirip map oluştur
                        val mealMap = mealIds.associateWith { mealId ->
                            mealDao.getMealById(mealId)
                        }

                        // UI güncellemesi ana thread'de yapılmalıdır
                        withContext(Dispatchers.Main) {
                            entries = allEntries
                            meals = mealMap as Map<Int, Meal>
                            loading = false
                        }
                    }
                }

                // Yükleme devam ediyorsa yükleme göstergesi göster
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Fiziksel geri tuşunu yakalayarak aktiviteyi sonlandır
                    BackHandler {
                        finish()
                    }

                    // Yükleme bittiğinde geçmiş öğünleri listeleyen ekran
                    PastMealsScreen(
                        entries = entries,
                        meals = meals,
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}

/**
 * `PastMealsScreen`, geçmiş öğün kayıtlarını (`FoodJournalEntry`) listeleyen
 * ve kullanıcıya geri tuşu desteği sağlayan bir Compose fonksiyonudur.
 *
 * @param entries Kullanıcının geçmiş öğün kayıtlarının listesi.
 * @param meals Her bir `mealId` değerine karşılık gelen `Meal` nesnelerini içeren bir harita.
 * @param onBackClick Geri tuşuna tıklandığında veya fiziksel geri tuşu basıldığında çağrılacak fonksiyon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastMealsScreen(
    entries: List<FoodJournalEntry>,
    meals: Map<Int, Meal>,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geçmiş Öğünlerim") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        // Eğer liste boşsa uyarı göster
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Geçmiş öğün bulunamadı.")
            }
        } else {
            // Kayıtları listele
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                items(entries) { entry ->
                    PastMealItem(entry = entry, meal = meals[entry.mealId])
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * `PastMealItem`, tek bir geçmiş öğün kaydını görüntüleyen bir satır bileşenidir.
 *
 * @param entry Geçmiş öğün kaydını temsil eden `FoodJournalEntry`.
 * @param meal İlgili öğünün `Meal` nesnesi. Boş olabilir (`null`), bu yüzden isteğe bağlı olarak kullanılır.
 */
@Composable
fun PastMealItem(entry: FoodJournalEntry, meal: Meal?) {
    // Tarih ve saati uygun biçimde formatla
    val formattedDateTime = remember(entry.dateTime) {
        try {
            entry.dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
        } catch (e: Exception) {
            "Geçersiz Tarih"
        }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Kayıtta saklanan öğün adını göster
            Text(
                text = entry.mealName,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Toplam kalori gösterimi
            Text(
                text = "Toplam Kalori: ${formatNumber(entry.totalCalories)} kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(formattedDateTime, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Text("Malzemeler ve Değişim Miktarları:", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))

            if (entry.ingredients.isEmpty()) {
                Text("Değişim seçilmemiş.")
            } else {
                // Seçilen her malzemeyi ve miktarını (değişim & gram) göster
                entry.ingredients.forEach { ing ->
                    val baseDisplay = ing.amount * ing.baseAmount
                    Text(
                        "${ing.name}: ${formatNumber(ing.amount)} = " +
                                "${formatNumber(baseDisplay)} ${ing.unit}"
                    )
                }
            }
        }
    }
}
