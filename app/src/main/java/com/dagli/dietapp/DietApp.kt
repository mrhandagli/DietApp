package com.dagli.dietapp

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * DietApp sınıfı, uygulamanın ana giriş noktası olarak [Application] sınıfını genişletir.
 *
 * @constructor Herhangi bir parametre almamakta, ancak [Application] sınıfını genişletiyor.
 */
class DietApp : Application() {

    /**
     * Veritabanı bağlantısı için kullanılacak [AppDatabase] türünde bir değişken.
     */
    lateinit var db: AppDatabase

    /**
     * Uygulamanın oluşturulduğunda çağrılır. Veritabanı, Room kütüphanesiyle yapılandırılır.
     *
     * @see Room.databaseBuilder
     */
    override fun onCreate() {
        super.onCreate()
        // Room kütüphanesi ile veritabanı oluşturma
        db = Room.databaseBuilder(
            applicationContext,  // Uygulama bağlamı (context)
            AppDatabase::class.java, // Veritabanı sınıfı
            "my-app-database"    // Veritabanı adı
        ).build()
    }

    /**
     * Verilerin yüklenmesini gerektiren bir senaryoda (örneğin ilk başlatma) çağrılabilir.
     * foods.json dosyasından yiyecek verilerini yükler ve veritabanına ekler.
     *
     * @receiver Uygulama sınıfının içinden çağrılır.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun loadFoodsIfNeeded() {
        // Uygulama tercihlerini alır
        val sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        // "foods_loaded" anahtarı ile verilerin daha önce yüklenip yüklenmediğini kontrol eder
        val foodsLoaded = sharedPref.getBoolean("foods_loaded", false)
        if (!foodsLoaded) {
            // Eğer yiyecekler yüklenmemişse, arka planda işlem başlatılır
            GlobalScope.launch(Dispatchers.IO) {
                val foods = loadFoodsFromJson()
                // Yiyecek verileri veritabanına eklenir
                db.foodDao().insertFoods(foods)
                // Yükleme işlemi tamamlandığı için "foods_loaded" true yapılır
                sharedPref.edit().putBoolean("foods_loaded", true).apply()
            }
        }
    }

    /**
     * assets/foods.json dosyasından yiyecek verisini yükleyen fonksiyon.
     *
     * @return [List] tipinde [Food] nesnelerini döndürür. Dosyadan okunup parse edilir.
     *
     * @see FoodRoot
     * @see Food
     */
    private fun loadFoodsFromJson(): List<Food> {
        // assets klasöründeki foods.json dosyasını açar
        val inputStream = assets.open("foods.json")
        // Dosyadaki JSON içeriğini okur
        val json = inputStream.bufferedReader().use { it.readText() }
        // JSON parse işlemi için Gson kütüphanesi
        val gson = Gson()
        // JSON verisini FoodRoot veri sınıfına dönüştürür
        val root = gson.fromJson(json, FoodRoot::class.java)
        // FoodRoot içerisindeki foodExchangeTable listesini döndürür
        return root.foodExchangeTable
    }
}
/**
 * `FoodRoot` sınıfı, bir `Food` listesini (besin değişim tablosu) içeren
 * kök model (root model) olarak kullanılır.
 *
 * @property foodExchangeTable Besin değişim tablosunu temsil eden `Food` nesnelerinin listesi.
 */
data class FoodRoot(
    val foodExchangeTable: List<Food>
)
