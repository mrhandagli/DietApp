package com.dagli.dietapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * `MealPlanEditActivity`, kullanıcının öğün planını düzenlemesine olanak tanır.
 * Veritabanındaki mevcut öğünleri yükler, Composable arayüzü üzerinden düzenlemeleri
 * sağlar ve değişiklikleri tekrar veritabanına kaydeder.
 */
class MealPlanEditActivity : ComponentActivity() {

    /**
     * Aktivite oluşturulduğunda çağrılır.
     *
     * @param savedInstanceState Önceki durum bilgilerini (örn. ekran döndürme) tutan `Bundle`.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DietApp

        setContent {
            MaterialTheme {
                // Mevcut öğünleri (MealUI) saklamak için bir liste
                var allMeals = remember { mutableStateListOf<MealUI>() }
                var loaded by remember { mutableStateOf(false) }

                /**
                 * 1. Adım: Veritabanından öğünleri (Meal) yükleyip
                 *    `MealUI` nesnelerine dönüştürmek için LaunchedEffect bloğu.
                 */
                LaunchedEffect(true) {
                    val mealsFromDb = withContext(Dispatchers.IO) {
                        app.db.mealDao().getAllMeals()
                    }

                    // MealUI listesi oluştur
                    val mealUIList = mutableListOf<MealUI>()

                    // Ana öğünlerin sırasını takip edecek liste
                    val mainMealIdsInOrder = mutableListOf<Int>()

                    // İsteğe göre öğünleri sıralama (örn. ID'ye göre)
                    val sortedMeals = mealsFromDb.sortedBy { it.id }

                    // Ana öğünleri ekle
                    sortedMeals.forEach { meal ->
                        if (meal.type == MealType.MAIN_MEAL) {
                            mainMealIdsInOrder.add(meal.id)
                            mealUIList.add(
                                MealUI(
                                    id = meal.id,
                                    name = meal.name,
                                    type = meal.type,
                                    startTime = meal.startTime,
                                    endTime = meal.endTime,
                                    parentIndex = null // Ana öğünlerin parentIndex'i olmadığı için null
                                )
                            )
                        }
                    }
                    // Ana öğünlere bağlı "Ara öğün" (SNACK) ekle
                    sortedMeals.forEach { meal ->
                        if (meal.type == MealType.SNACK) {
                            val parentIndex = mainMealIdsInOrder.indexOf(meal.parentMealId)
                            mealUIList.add(
                                MealUI(
                                    id = meal.id,
                                    name = meal.name,
                                    type = meal.type,
                                    startTime = meal.startTime,
                                    endTime = meal.endTime,
                                    parentIndex = if (parentIndex >= 0) parentIndex else null
                                )
                            )
                        }
                    }

                    // Elde edilen tüm `MealUI` öğelerini `allMeals` listesine ekle
                    allMeals.addAll(mealUIList)

                    // Orijinal meal ID'lerini SharedPreferences'ta saklama (yalnızca ilk sefer)
                    val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    if (!sharedPref.contains("OriginalMealIds")) {
                        sharedPref.edit()
                            .putStringSet(
                                "OriginalMealIds",
                                allMeals.mapNotNull { it.id }.map { it.toString() }.toSet()
                            )
                            .apply()
                    }

                    // Yükleme tamamlandı
                    loaded = true
                }

                // Yükleme devam ederken kullanıcıya gösterilecek ekran
                if (!loaded) {
                    LoadingUI()
                } else {
                    /**
                     * 2. Adım: `MealPlanningScreen` composable'ını göster.
                     * Kullanıcı, öğün planını düzenledikten sonra "onNextClick" ile
                     * `DistributionEditActivity` ekranına yönlendirilir.
                     */
                    MealPlanningScreen(
                        initialMeals = allMeals,
                        onNextClick = { mealUIList ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)

                                // Orijinal meal ID setini geri al
                                val originalMealIds = sharedPref
                                    .getStringSet("OriginalMealIds", emptySet())
                                    ?.map { it.toInt() }
                                    ?.toSet() ?: emptySet()

                                // Düzenlenen listeden yeni meal ID setini çıkar
                                val newMealIds = mealUIList.mapNotNull { it.id }.toSet()

                                // Eklenen ve kaldırılan öğünleri belirle
                                val addedMeals = newMealIds - originalMealIds
                                val removedMeals = originalMealIds - newMealIds

                                // Mevcut meal ID setini SharedPreferences'e yaz
                                sharedPref.edit()
                                    .putStringSet("OriginalMealIds", newMealIds.map { it.toString() }.toSet())
                                    .apply()

                                // Eğer eklenen veya silinen öğün varsa, besin dağılımını resetle
                                if (addedMeals.isNotEmpty() || removedMeals.isNotEmpty()) {
                                    sharedPref.edit().remove("NutrientDistribution").apply()
                                    sharedPref.edit().putBoolean("nutrient_split_set", false).apply()
                                }

                                // Veritabanındaki öğünleri (Meals) güncelle
                                val existingMeals = withContext(Dispatchers.IO) {
                                    app.db.mealDao().getAllMeals()
                                }
                                val existingMealIds = existingMeals.map { it.id }.toSet()
                                val updatedMealIds = mealUIList.mapNotNull { it.id }.toSet()

                                // Silinecek öğünleri belirle
                                val mealsToDelete = existingMeals.filter { it.id !in updatedMealIds }
                                if (mealsToDelete.isNotEmpty()) {
                                    app.db.mealDao().deleteMeals(mealsToDelete)
                                }

                                // Yeni eklenen (ID'si olmayan) öğünleri ekle
                                val mealsToInsert = mealUIList.filter { it.id == null }
                                val insertedMealIds = if (mealsToInsert.isNotEmpty()) {
                                    app.db.mealDao().insertMeals(
                                        mealsToInsert.map {
                                            Meal(
                                                name = it.name,
                                                type = it.type,
                                                startTime = it.startTime,
                                                endTime = it.endTime,
                                                parentMealId = if (it.type == MealType.SNACK && it.parentIndex != null) {
                                                    // parentIndex geçerli ise parentMealId'yi al
                                                    val parentMealUI = mealUIList.getOrNull(it.parentIndex)
                                                    parentMealUI?.id
                                                } else {
                                                    null
                                                }
                                            )
                                        }
                                    )
                                } else {
                                    emptyList<Long>()
                                }

                                // Mevcut öğünleri güncelle
                                val mealsToUpdate = mealUIList.filter { it.id != null }
                                mealsToUpdate.forEach { mealUI ->
                                    app.db.mealDao().updateMeal(
                                        Meal(
                                            id = mealUI.id!!,
                                            name = mealUI.name,
                                            type = mealUI.type,
                                            startTime = mealUI.startTime,
                                            endTime = mealUI.endTime,
                                            parentMealId = if (mealUI.type == MealType.SNACK && mealUI.parentIndex != null) {
                                                mealUIList[mealUI.parentIndex].id
                                            } else {
                                                null
                                            }
                                        )
                                    )
                                }

                                // DistributionEditActivity ekranına geçiş yap
                                withContext(Dispatchers.Main) {
                                    val intent = Intent(
                                        this@MealPlanEditActivity,
                                        DistributionEditActivity::class.java
                                    )
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
 * Basit bir yükleme göstergesi (loading spinner) içeren composable fonksiyon.
 */
@Composable
fun LoadingUI() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
