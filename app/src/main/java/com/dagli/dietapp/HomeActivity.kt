package com.dagli.dietapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * HomeActivity, uygulamanın ana ekranını yönetir. Kullanıcıya güncel öğünler,
 * yaklaşan öğünler ve menü seçenekleri gibi temel işlevleri sunar.
 */
class HomeActivity : ComponentActivity() {

    // Gösterilecek yaklaşan öğün sayısı
    private val upcomingMealCount = 2

    /**
     * Aktivite oluşturulduğunda çağrılır.
     *
     * @param savedInstanceState Önceki durum bilgileri (örn. ekran döndürme) taşır.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DietApp

        setContent {
            MaterialTheme {
                // Ekranda kullanılacak durum değişkenleri
                var currentMeals by remember { mutableStateOf<List<Meal>>(emptyList()) }
                var upcomingMeals by remember { mutableStateOf<List<Meal>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }
                var distributionMap by remember { mutableStateOf<Map<Int, Map<String, Double>>>(emptyMap()) }
                var username by remember { mutableStateOf("") } // Kullanıcı adı için state


                // Veri yükleme işlemlerini başlatma
                LaunchedEffect(true) {
                    withContext(Dispatchers.IO) {
                        // Besin dağılımı verilerini yükleme
                        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                        distributionMap = loadDistribution(sharedPref)

                        // Kullanıcı adını yükleme
                        username = loadUsername(sharedPref)

                        // Veritabanından öğünleri yükleme
                        val mealsFromDb = app.db.mealDao().getAllMeals()
                        Log.d("HomeActivity", "Loaded meals from DB: $mealsFromDb")

                        // Mevcut saat
                        val now = LocalTime.now()
                        Log.d("HomeActivity", "Current time: $now")

                        // Öğünleri, belirtilen saatte hangi öğünlerin önce geldiğine göre sıralama
                        val sortedMeals = mealsFromDb.sortedWith(compareBy { getNextStartTime(it, now) })
                        Log.d("HomeActivity", "Sorted meals: $sortedMeals")

                        // Şu anda aktif olan öğünleri filtreleme
                        val current = sortedMeals.filter { isCurrentMeal(it, now) }
                        Log.d("HomeActivity", "Current meals: $current")

                        // Yaklaşan öğünleri filtreleme
                        val upcomingCandidates = sortedMeals.filter { isUpcomingMeal(it, now) }
                        Log.d("HomeActivity", "Upcoming candidates: $upcomingCandidates")

                        // Belirli sayıda yaklaşan öğünleri seçme
                        val finalUpcomingMeals = upcomingCandidates.take(upcomingMealCount)
                        Log.d("HomeActivity", "Final upcoming meals: $finalUpcomingMeals")

                        // Ana ekrana durum (state) değerlerini güncelleme
                        withContext(Dispatchers.Main) {
                            currentMeals = current
                            upcomingMeals = finalUpcomingMeals
                            loading = false
                        }
                    }
                }

                // Yükleme ekranı veya içerik ekranı
                if (loading) {
                    // Yükleme göstergesi
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Asıl ekran içeriği
                    HomeScreen(
                        username = username, // Kullanıcı adı
                        currentMeals = currentMeals, //Güncel Öğünler
                        upcomingMeals = upcomingMeals, //Gelecek Öğünler
                        distributionMap = distributionMap, //Dağıtımlar
                        onSettingsClick = {
                            val intent = Intent(this@HomeActivity, SettingsActivity::class.java)
                            startActivity(intent)
                        },
                        onCurrentMealClick = { meal ->
                            val intent = Intent(this@HomeActivity, IngredientAddingActivity::class.java)
                            intent.putExtra("MEAL_ID", meal.id)
                            startActivity(intent)
                        },
                        onPastMealsClick = {
                            val intent = Intent(this@HomeActivity, PastMealsActivity::class.java)
                            startActivity(intent)
                        },
                        onEditMealPlanClick = {
                            val intent = Intent(this@HomeActivity, MealPlanEditActivity::class.java)
                            startActivity(intent)
                        },
                        onDistributionEditClick = {
                            val intent = Intent(this@HomeActivity, DistributionEditActivity::class.java)
                            startActivity(intent)
                        },
                        onExchangeEditClick = {
                            val intent = Intent(this@HomeActivity, ExchangeEditActivity::class.java)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    /**
     * Öğünün, belirlenen [now] zamanına göre şu anda aktif olup olmadığını kontrol eder.
     *
     * @param meal Kontrol edilecek öğün nesnesi.
     * @param now Mevcut zamanı temsil eden [LocalTime].
     * @return Öğün şu anda aktifse `true`, aksi takdirde `false`.
     */
    private fun isCurrentMeal(meal: Meal, now: LocalTime): Boolean {
        // 24 saatlik öğün (örnek: gece yarısından gece yarısına)
        if (meal.startTime == meal.endTime) {
            return true
        }

        return if (meal.startTime < meal.endTime) {
            // Aynı güne ait öğün: örn. 09:00 -> 12:00
            now >= meal.startTime && now < meal.endTime
        } else {
            // Gece yarısını kapsayan öğün: örn. 22:00 -> 02:00 (ertesi gün)
            // "Aktif" kabul etmek için (22:00 <= now) veya (now < 02:00)
            now >= meal.startTime || now < meal.endTime
        }
    }

    /**
     * Öğünün, belirlenen [now] zamanına göre yaklaşıyor (gelecek) olup olmadığını kontrol eder.
     *
     * @param meal Kontrol edilecek öğün nesnesi.
     * @param now Mevcut zamanı temsil eden [LocalTime].
     * @return Öğün gelecek ise `true`, aksi takdirde `false`.
     */
    private fun isUpcomingMeal(meal: Meal, now: LocalTime): Boolean {
        // 24 saatlik öğünler 'upcoming' yani gelecek olarak sayılmaz
        if (meal.startTime == meal.endTime) {
            return false
        }

        return if (meal.startTime < meal.endTime) {
            // Aynı güne ait öğün
            meal.startTime > now
        } else {
            // Gece yarısını kapsayan öğün
            // Hem start hem de end zamanı, `now` sonrasındaysa 'upcoming' kabul edilir
            meal.startTime > now && meal.endTime > now
        }
    }

    /**
     * Belirli bir öğünün [now] zamanına göre bir sonraki başlangıç saatini döndürür.
     * Eğer öğün başlangıç saati gün içinde geçmişse, ertesi güne eklenir.
     *
     * @param meal Zamanı hesaplanacak öğün nesnesi.
     * @param now Mevcut zamanı temsil eden [LocalTime].
     * @return Öğünün gün içindeki bir sonraki başlangıç zamanı.
     */
    private fun getNextStartTime(meal: Meal, now: LocalTime): LocalTime {
        return if (meal.startTime < now && meal.startTime < meal.endTime) {
            // Öğün bugünkü saatini geçmiş ve gece yarısını kapsamıyorsa
            meal.startTime.plusHours(24)
        } else {
            meal.startTime
        }
    }

    /**
     * SharedPreferences'tan besin dağılımı verisini yükler.
     *
     * @param sharedPref Besin dağılımını çekmek için kullanılan [android.content.SharedPreferences] örneği.
     * @return Her öğünün ID'sine karşılık gelen besin dağılımlarını tutan bir Map.
     */
    private fun loadDistribution(sharedPref: android.content.SharedPreferences): Map<Int, Map<String, Double>> {
        val loadedDistributionJson = sharedPref.getString("NutrientDistribution", "{}")
        val type = object : TypeToken<Map<String, Map<String, Double>>>() {}.type

        val loadedDistributionStringMap: Map<String, Map<String, Double>> = try {
            Gson().fromJson(loadedDistributionJson, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e("HomeActivity", "Error parsing distribution JSON", e)
            emptyMap()
        }

        // Map<String, Map<String, Double>> -> Map<Int, Map<String, Double>> dönüşümü
        val loadedDistributionIntMap: Map<Int, Map<String, Double>> =
            loadedDistributionStringMap.mapKeys { it.key.toIntOrNull() ?: -1 }
                .filterKeys { it != -1 } // Int'e dönüşemeyenleri ayıkla

        return loadedDistributionIntMap
    }

    /**
     * SharedPreferences'tan kullanıcı adını yükler.
     *
     * @param sharedPref Kullanıcı adını çekmek için kullanılan [android.content.SharedPreferences].
     * @return Bulunması durumunda kaydedilmiş kullanıcı adı; aksi halde boş string.
     */
    private fun loadUsername(sharedPref: android.content.SharedPreferences): String {
        return sharedPref.getString("USERNAME_KEY", "") ?: ""
    }

    /**
     * Ana ekranın (HomeScreen) Compose fonksiyonu. Kullanıcıya mevcut ve gelecek öğünleri,
     * menü seçeneklerini ve kişiselleştirilmiş selamlama metnini gösterir.
     *
     * @param username Kullanıcının adı (Kişiselleştirilmiş selamlama için).
     * @param currentMeals Şu anda aktif olan öğünlerin listesi.
     * @param upcomingMeals Yaklaşan öğünlerin listesi.
     * @param distributionMap Öğün başına besin dağılım verilerinin haritası.
     * @param onSettingsClick Ayarlar ekranına geçiş için tıklama işlevi.
     * @param onCurrentMealClick Güncel öğüne tıklandığında çağrılacak işlev.
     * @param onPastMealsClick Geçmiş öğünler ekranına geçiş için tıklama işlevi.
     * @param onEditMealPlanClick Öğün planını düzenleme ekranına geçiş için tıklama işlevi.
     * @param onExchangeEditClick Besin değişimlerini düzenleme ekranına geçiş için tıklama işlevi.
     * @param onDistributionEditClick Besin dağılımlarını düzenleme ekranına geçiş için tıklama işlevi.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HomeScreen(
        username: String,
        currentMeals: List<Meal>,
        upcomingMeals: List<Meal>,
        distributionMap: Map<Int, Map<String, Double>>,
        onSettingsClick: () -> Unit,
        onCurrentMealClick: (Meal) -> Unit,
        onPastMealsClick: () -> Unit,
        onEditMealPlanClick: () -> Unit,
        onExchangeEditClick: () -> Unit,
        onDistributionEditClick: () -> Unit
    ) {
        var menuExpanded by remember { mutableStateOf(false) }

        // Menü ögeleri
        val menuItems = listOf(
            MenuItem("Geçmiş Öğünlerim", Icons.Default.History, onPastMealsClick),
            MenuItem("Öğün Planım", Icons.Default.EditNote, onEditMealPlanClick),
            MenuItem("Değişimlerim", Icons.Default.Edit, onExchangeEditClick),
            MenuItem("Dağıtımlarım", Icons.Default.Edit, onDistributionEditClick),
            MenuItem("Tartı Kurulumu", Icons.Default.Settings, onSettingsClick)
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("DietApp") },
                    actions = {
                        // Sağ üst köşedeki menü butonu
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu"
                            )
                        }
                        AppDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            items = menuItems
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Kişiselleştirilmiş Karşılama
                Text(
                    text = if (username.isNotBlank()) "Merhaba, $username!" else "Merhaba!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Şu Anki (Aktif) Öğünler
                if (currentMeals.isNotEmpty()) {
                    Text(
                        "Aktif Öğünlerim",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    currentMeals.forEach { meal ->
                        MealCard(
                            title = if (meal.type == MealType.MAIN_MEAL) "Ana öğün" else "Ara öğün",
                            meal = meal,
                            distributionMap = distributionMap,
                            onClick = { onCurrentMealClick(meal) }
                        )
                    }
                } else {
                    NoMealCard(title = "Aktif Öğünler", message = "Aktif öğün yok.")
                }

                // Gelecek Öğünler
                if (upcomingMeals.isNotEmpty()) {
                    Text(
                        "Gelecek Öğünlerim",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    upcomingMeals.forEachIndexed { idx, meal ->
                        MealCard(
                            title = if (idx == 0) "Gelecek Öğün" else "Diğer Öğünlerim",
                            meal = meal,
                            distributionMap = distributionMap,
                            onClick = { /* Opsiyonel: Bu öğüne tıklandığında işlem yapılabilir */ }
                        )
                    }
                } else {
                    NoMealCard(title = "Gelecek Öğünlerim", message = "Gelecek öğün yok.")
                }
            }
        }
    }

    /**
     * Uygulamanın açılır menüsünü (DropdownMenu) oluşturan Compose fonksiyonu.
     *
     * @param expanded Menü açık/kapalı durumunu belirler.
     * @param onDismissRequest Menü dışına tıklanma veya kapatılması istendiğinde çağrılır.
     * @param items Menünün kalemlerini temsil eden [MenuItem] listesi.
     */
    @Composable
    fun AppDropdownMenu(
        expanded: Boolean,
        onDismissRequest: () -> Unit,
        items: List<MenuItem>
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .clip(RoundedCornerShape(8.dp)) // Menü için yuvarlatılmış köşeler
        ) {
            items.forEach { item ->
                if (item.label == "Tartı Kurulumu") {
                    HorizontalDivider() // Ayarlar öğesini ayırmak için
                }
                DropdownMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = "${item.label} Icon"
                        )
                    },
                    onClick = {
                        onDismissRequest()
                        item.onClick()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }

    /**
     * Menü öğesi veri sınıfı. Her öğe için bir etiket, simge ve tıklama işlevi tutar.
     *
     * @property label Menüde gösterilecek metin.
     * @property icon Menü öğesinin yanında gösterilecek [ImageVector] simge.
     * @property onClick Menü öğesine tıklandığında tetiklenecek işlev.
     */
    data class MenuItem(
        val label: String,
        val icon: ImageVector,
        val onClick: () -> Unit
    )

    /**
     * Herhangi bir öğün olmadığı durumlarda gösterilen kart.
     *
     * @param title Kart başlığı (örnek: "Aktif Öğünler").
     * @param message Bilgilendirici mesaj (örnek: "Aktif öğün yok.").
     */
    @Composable
    fun NoMealCard(title: String, message: String) {
        Surface(
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    /**
     * Öğünleri listelemek için kullanılan kart bileşeni (Composable).
     *
     * @param title Kart başlığında gösterilecek ek açıklama (örn. "Ana öğün").
     * @param meal Gösterilecek [Meal] nesnesi.
     * @param distributionMap Öğünün besin dağılımına dair veri haritası.
     * @param onClick Kart tıklandığında çağrılacak işlev.
     */
    @Composable
    fun MealCard(
        title: String,
        meal: Meal,
        distributionMap: Map<Int, Map<String, Double>>,
        onClick: () -> Unit
    ) {
        // İlgili öğünün besin dağılımı verilerini alma
        val nutrientsForMeal = distributionMap[meal.id] ?: emptyMap()

        // Toplam kalori hesaplama
        val totalCalories = CalorieCalculator.computeTotalCalories(nutrientsForMeal).roundToInt()

        Surface(
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Öğün türüne göre simge seçimi
                Icon(
                    imageVector = if (meal.type == MealType.MAIN_MEAL) Icons.Default.Restaurant else Icons.Default.Cookie,
                    contentDescription = "${meal.name} Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            meal.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "$totalCalories kcal",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Saat: ${meal.startTime} - ${meal.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
