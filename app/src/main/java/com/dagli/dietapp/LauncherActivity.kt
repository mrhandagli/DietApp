package com.dagli.dietapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * LauncherActivity, uygulamanın ilk açıldığında hangi ekrana gideceğini belirler.
 * Kullanıcının tamamlamış olması gereken adımları SharedPreferences üzerinden kontrol eder.
 */
class LauncherActivity : ComponentActivity() {
    /**
     * @param savedInstanceState Daha önce kaydedilmiş durum bilgilerini içerir (ör. döndürme).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kullanıcı tercihlerini yüklemek için SharedPreferences kullanılır
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)

        // Kullanıcının kullanıcı adını ayarlayıp ayarlamadığı kontrol edilir
        val usernameSet = prefs.getBoolean("username_set", false)
        // Besin değişim tablosunun ayarlanıp ayarlanmadığı kontrol edilir
        val foodExchangeSet = prefs.getBoolean("food_exchange_set", false)
        // Yemek planının oluşturulup oluşturulmadığı kontrol edilir
        val mealPlanSet = prefs.getBoolean("meal_plan_set", false)
        // Besin bölünmesi (nutrient split) tamamlanıp tamamlanmadığı kontrol edilir
        val nutrientSplitSet = prefs.getBoolean("nutrient_split_set", false)

        /**
         * Kullanıcının henüz tamamlamadığı ilk adımı tespit ederek
         * uygun aktiviteye yönlendirme yapar.
         *
         * @return Belirlenen sınıf [Class] türünden olup, açılacak olan aktiviteyi temsil eder.
         */
        val nextActivity = when {
            !usernameSet -> WelcomeActivity::class.java
            // Eğer kullanıcı adı ayarlanmamışsa WelcomeActivity'ye yönlendir
            !foodExchangeSet -> ExchangeActivity::class.java
            // Eğer besin değişim tablosu oluşturulmamışsa FoodExchangeActivity'ye yönlendir
            !mealPlanSet -> MealPlanningActivity::class.java
            // Eğer yemek planı oluşturulmamışsa MealPlanningActivity'ye yönlendir
            !nutrientSplitSet -> DistributionActivity::class.java
            // Eğer besin bölünmesi tamamlanmamışsa NutrientSplittingActivity'ye yönlendir
            else -> HomeActivity::class.java
            // Tüm adımlar tamamlandıysa HomeActivity'ye yönlendir
        }

        // Bulunan aktiviteyi başlat ve mevcut aktiviteyi (LauncherActivity) kapat
        startActivity(Intent(this, nextActivity))
        finish()
    }
}
